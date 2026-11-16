package net.deizyn.bhfx.impl.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bakes a Blockbench / GeckoLib {@code .geo.json} (format 1.12.0 and later) into a triangle list, the same way
 * GeckoLib 4 does: x mirrored, positions in blocks (pixels / 16), box and per-face UVs, face UV rotation, cube and bone
 * rotations (Z, then Y, then X, round their pivots; x and y angles negated), the rest pose of every bone applied, cube
 * {@code mirror} and {@code inflate}. Bones marked {@code neverRender} are left out. Pure Java (no GL), so it runs on a
 * background thread and in tests.
 * <p>
 * Vertex layout ({@link #FLOATS} floats): position xyz, normal xyz, uv, bone index. Triangles face outward
 * (counter-clockwise seen from the side the normal points to), so back faces can be culled.
 */
public final class GeoModelLoader {
    public static final int FLOATS = 9;

    private GeoModelLoader() {
    }

    /** The baked model. {@code min} / {@code max}: its bounds in model space (blocks). */
    public record Data(float[] vertices, int vertexCount, String[] boneNames, float[] min, float[] max) {
    }

    private enum Face {
        WEST(-1, 0, 0, "west"), EAST(1, 0, 0, "east"), NORTH(0, 0, -1, "north"), SOUTH(0, 0, 1, "south"),
        UP(0, 1, 0, "up"), DOWN(0, -1, 0, "down");

        final int nx;
        final int ny;
        final int nz;
        final String key;

        Face(int nx, int ny, int nz, String key) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.key = key;
        }
    }

    private record BoneDef(String name, String parent, float[] pivot, float[] rotation, Float inflate,
                           boolean neverRender, JsonArray cubes) {
    }

    public static Data load(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray geos = root.has("minecraft:geometry") ? root.getAsJsonArray("minecraft:geometry") : null;
        if (geos == null || geos.isEmpty()) {
            throw new IllegalArgumentException("not a .geo.json of format 1.12.0 or later (no minecraft:geometry)");
        }
        JsonObject geo = geos.get(0).getAsJsonObject();
        JsonObject desc = geo.has("description") ? geo.getAsJsonObject("description") : new JsonObject();
        float texW = desc.has("texture_width") ? desc.get("texture_width").getAsFloat() : 64f;
        float texH = desc.has("texture_height") ? desc.get("texture_height").getAsFloat() : 64f;

        Map<String, BoneDef> bones = new LinkedHashMap<>();
        if (geo.has("bones")) {
            for (JsonElement el : geo.getAsJsonArray("bones")) {
                JsonObject b = el.getAsJsonObject();
                String name = b.get("name").getAsString();
                bones.put(name, new BoneDef(name, b.has("parent") ? b.get("parent").getAsString() : null,
                        vec(b, "pivot"), vec(b, "rotation"), b.has("inflate") ? b.get("inflate").getAsFloat() : null,
                        b.has("neverRender") && b.get("neverRender").getAsBoolean(),
                        b.has("cubes") ? b.getAsJsonArray("cubes") : new JsonArray()));
            }
        }

        Map<String, Matrix4f> rest = new HashMap<>();
        List<String> names = new ArrayList<>(bones.keySet());
        Mesh mesh = new Mesh();
        for (int bi = 0; bi < names.size(); bi++) {
            BoneDef bone = bones.get(names.get(bi));
            if (hidden(bone, bones)) {
                continue;
            }
            Matrix4f m = restMatrix(bone, bones, rest);
            for (JsonElement el : bone.cubes()) {
                cube(el.getAsJsonObject(), bone, m, bi, texW, texH, mesh);
            }
        }
        return new Data(mesh.toArray(), mesh.count, names.toArray(new String[0]), mesh.min, mesh.max);
    }

    private static boolean hidden(BoneDef bone, Map<String, BoneDef> bones) {
        for (BoneDef b = bone; b != null; b = b.parent() != null ? bones.get(b.parent()) : null) {
            if (b.neverRender()) {
                return true;
            }
        }
        return false;
    }

    /** Model-space matrix of a bone at rest: parent's, then this bone's rotation round its pivot. */
    private static Matrix4f restMatrix(BoneDef bone, Map<String, BoneDef> bones, Map<String, Matrix4f> cache) {
        Matrix4f cached = cache.get(bone.name());
        if (cached != null) {
            return cached;
        }
        BoneDef parent = bone.parent() != null ? bones.get(bone.parent()) : null;
        Matrix4f m = parent != null && parent != bone ? new Matrix4f(restMatrix(parent, bones, cache)) : new Matrix4f();
        float px = -bone.pivot()[0] / 16f;
        float py = bone.pivot()[1] / 16f;
        float pz = bone.pivot()[2] / 16f;
        m.translate(px, py, pz);
        rotateZYX(m, (float) Math.toRadians(-bone.rotation()[0]), (float) Math.toRadians(-bone.rotation()[1]),
                (float) Math.toRadians(bone.rotation()[2]));
        m.translate(-px, -py, -pz);
        cache.put(bone.name(), m);
        return m;
    }

    private static void rotateZYX(Matrix4f m, float rx, float ry, float rz) {
        if (rz != 0f) {
            m.rotateZ(rz);
        }
        if (ry != 0f) {
            m.rotateY(ry);
        }
        if (rx != 0f) {
            m.rotateX(rx);
        }
    }

    private static void cube(JsonObject c, BoneDef bone, Matrix4f boneMatrix, int boneIndex, float texW, float texH,
                             Mesh mesh) {
        float[] origin = vec(c, "origin");
        float[] size = vec(c, "size");
        float[] pivot = vec(c, "pivot");
        float[] rot = vec(c, "rotation");
        boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
        float inflate = (c.has("inflate") ? c.get("inflate").getAsFloat() : bone.inflate() != null ? bone.inflate() : 0f) / 16f;

        float ox = -(origin[0] + size[0]) / 16f;
        float oy = origin[1] / 16f;
        float oz = origin[2] / 16f;
        float sx = size[0] / 16f;
        float sy = size[1] / 16f;
        float sz = size[2] / 16f;
        // GeckoLib's VertexSet, names as it has them
        float[] blb = {ox - inflate, oy - inflate, oz - inflate};
        float[] brb = {ox - inflate, oy - inflate, oz + sz + inflate};
        float[] tlb = {ox - inflate, oy + sy + inflate, oz - inflate};
        float[] trb = {ox - inflate, oy + sy + inflate, oz + sz + inflate};
        float[] tlf = {ox + sx + inflate, oy + sy + inflate, oz - inflate};
        float[] trf = {ox + sx + inflate, oy + sy + inflate, oz + sz + inflate};
        float[] blf = {ox + sx + inflate, oy - inflate, oz - inflate};
        float[] brf = {ox + sx + inflate, oy - inflate, oz + sz + inflate};

        Matrix4f m = new Matrix4f(boneMatrix);
        float cpx = -pivot[0] / 16f;
        float cpy = pivot[1] / 16f;
        float cpz = pivot[2] / 16f;
        m.translate(cpx, cpy, cpz);
        rotateZYX(m, (float) Math.toRadians(-rot[0]), (float) Math.toRadians(-rot[1]), (float) Math.toRadians(rot[2]));
        m.translate(-cpx, -cpy, -cpz);
        Matrix3f normalMatrix = new Matrix3f(m).invert().transpose();

        JsonElement uvEl = c.get("uv");
        boolean boxUv = uvEl == null || uvEl.isJsonArray();
        float[] boxUvAt = boxUv ? (uvEl == null ? new float[]{0, 0} : vec2(uvEl.getAsJsonArray())) : null;
        for (Face face : Face.values()) {
            float u;
            float v;
            float us;
            float vs;
            int uvRotation = 0;
            if (boxUv) {
                float fx = (float) Math.floor(size[0]);
                float fy = (float) Math.floor(size[1]);
                float fz = (float) Math.floor(size[2]);
                float bu = boxUvAt[0];
                float bv = boxUvAt[1];
                switch (face) {
                    case WEST -> { u = bu + fz + fx; v = bv + fz; us = fz; vs = fy; }
                    case EAST -> { u = bu; v = bv + fz; us = fz; vs = fy; }
                    case NORTH -> { u = bu + fz; v = bv + fz; us = fx; vs = fy; }
                    case SOUTH -> { u = bu + fz + fx + fz; v = bv + fz; us = fx; vs = fy; }
                    case UP -> { u = bu + fz; v = bv; us = fx; vs = fz; }
                    default -> { u = bu + fz + fx; v = bv + fz; us = fx; vs = -fz; }
                }
            } else {
                JsonObject faces = uvEl.getAsJsonObject();
                if (!faces.has(face.key)) {
                    continue;
                }
                JsonObject f = faces.getAsJsonObject(face.key);
                float[] at = f.has("uv") ? vec2(f.getAsJsonArray("uv")) : new float[]{0, 0};
                float[] sz2 = f.has("uv_size") ? vec2(f.getAsJsonArray("uv_size")) : new float[]{0, 0};
                u = at[0];
                v = at[1];
                us = sz2[0];
                vs = sz2[1];
                uvRotation = f.has("uv_rotation") ? Math.floorMod(f.get("uv_rotation").getAsInt(), 360) / 90 : 0;
            }
            float[][] quad = switch (face) {
                case WEST -> mirror ? new float[][]{tlf, trf, brf, blf} : new float[][]{trb, tlb, blb, brb};
                case EAST -> mirror ? new float[][]{trb, tlb, blb, brb} : new float[][]{tlf, trf, brf, blf};
                case NORTH -> new float[][]{tlb, tlf, blf, blb};
                case SOUTH -> new float[][]{trf, trb, brb, brf};
                case UP -> mirror && !boxUv ? new float[][]{blb, blf, brf, brb} : new float[][]{trb, trf, tlf, tlb};
                default -> mirror && !boxUv ? new float[][]{trb, trf, tlf, tlb} : new float[][]{blb, blf, brf, brb};
            };
            // GeoQuad.build
            float u0 = u / texW;
            float v0 = v / texH;
            float u1 = (u + us) / texW;
            float v1 = (v + vs) / texH;
            float nx = face.nx;
            if (!mirror) {
                float t = u1;
                u1 = u0;
                u0 = t;
            } else {
                nx = -nx;
            }
            float[] uvs = switch (uvRotation) {
                case 1 -> new float[]{u1, v0, u1, v1, u0, v1, u0, v0};
                case 2 -> new float[]{u1, v1, u0, v1, u0, v0, u1, v0};
                case 3 -> new float[]{u0, v1, u0, v0, u1, v0, u1, v1};
                default -> new float[]{u0, v0, u1, v0, u1, v1, u0, v1};
            };
            Vector3f n = normalMatrix.transform(new Vector3f(nx, face.ny, face.nz));
            if (n.lengthSquared() > 1e-12f) {
                n.normalize();
            }
            Vector3f[] p = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                p[i] = m.transformPosition(new Vector3f(quad[i][0], quad[i][1], quad[i][2]));
            }
            mesh.quad(p, uvs, n, boneIndex);
        }
    }

    private static float[] vec(JsonObject o, String key) {
        if (!o.has(key)) {
            return new float[]{0, 0, 0};
        }
        JsonArray a = o.getAsJsonArray(key);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static float[] vec2(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    /** Growing vertex list; turns each quad into two outward-facing triangles. */
    private static final class Mesh {
        float[] data = new float[FLOATS * 6 * 64];
        int count;
        final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

        void quad(Vector3f[] p, float[] uv, Vector3f n, int bone) {
            tri(p, uv, n, bone, 0, 1, 2);
            tri(p, uv, n, bone, 0, 2, 3);
        }

        private void tri(Vector3f[] p, float[] uv, Vector3f n, int bone, int a, int b, int c) {
            Vector3f e1 = new Vector3f(p[b]).sub(p[a]);
            Vector3f e2 = new Vector3f(p[c]).sub(p[a]);
            float facing = e1.cross(e2).dot(n);
            if (facing == 0f && n.lengthSquared() == 0f) {
                return;
            }
            if (facing < 0f) {
                int t = b;
                b = c;
                c = t;
            }
            vertex(p[a], uv[a * 2], uv[a * 2 + 1], n, bone);
            vertex(p[b], uv[b * 2], uv[b * 2 + 1], n, bone);
            vertex(p[c], uv[c * 2], uv[c * 2 + 1], n, bone);
        }

        private void vertex(Vector3f p, float u, float v, Vector3f n, int bone) {
            if ((count + 1) * FLOATS > data.length) {
                data = java.util.Arrays.copyOf(data, data.length * 2);
            }
            int o = count * FLOATS;
            data[o] = p.x;
            data[o + 1] = p.y;
            data[o + 2] = p.z;
            data[o + 3] = n.x;
            data[o + 4] = n.y;
            data[o + 5] = n.z;
            data[o + 6] = u;
            data[o + 7] = v;
            data[o + 8] = bone;
            count++;
            min[0] = Math.min(min[0], p.x);
            min[1] = Math.min(min[1], p.y);
            min[2] = Math.min(min[2], p.z);
            max[0] = Math.max(max[0], p.x);
            max[1] = Math.max(max[1], p.y);
            max[2] = Math.max(max[2], p.z);
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, count * FLOATS);
        }
    }
}
