package net.deizyn.bhfx.impl.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;
import net.deizyn.bhfx.api.client.Uniforms;
import net.deizyn.bhfx.api.client.FxFrame;
import net.deizyn.bhfx.api.client.FxModel;
import net.deizyn.bhfx.api.client.FxPose;
import net.deizyn.bhfx.api.client.FxData;
import net.deizyn.bhfx.impl.render.DataTable;
import net.deizyn.bhfx.impl.render.ModelDraw;
import net.deizyn.bhfx.impl.render.ParticleBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Passes collected from every effect in one frame. */
public final class PassListImpl implements PassList {
    /**
     * {@code bx, by, bz} = camera-relative centre of the bounds; {@code radius <= 0} = whole screen (for particles: no
     * bounds given, the batch's own sphere is used). {@code particles} is set for a particle batch, drawn at {@code time};
     * {@code model} for a model ({@code shader} is then its material).
     */
    public record Entry(ResourceLocation shader, int order, int seq, double distance, Consumer<Uniforms> setup,
                        double bx, double by, double bz, double radius, @Nullable ParticleBatch particles, float time,
                        float presence, @Nullable ModelDraw model, float layer) {
        public Entry(ResourceLocation shader, int order, int seq, double distance, Consumer<Uniforms> setup,
                     double bx, double by, double bz, double radius, @Nullable ParticleBatch particles, float time,
                     float presence, @Nullable ModelDraw model) {
            this(shader, order, seq, distance, setup, bx, by, bz, radius, particles, time, presence, model, 0f);
        }

        /** Not a full-screen pass: a particle batch or a model. */
        public boolean drawsGeometry() {
            return particles != null || model != null;
        }
    }

    private static final Comparator<Entry> DRAW_ORDER = Comparator.comparingInt(Entry::order).thenComparingInt(Entry::seq);
    private static final Comparator<Entry> KEEP_ORDER =
            Comparator.comparingInt(Entry::order).thenComparingDouble(Entry::distance).thenComparingInt(Entry::seq);

    private static final net.minecraft.resources.ResourceLocation LIGHTS =
            new net.minecraft.resources.ResourceLocation("bhfx", "lights");

    private final List<Entry> entries = new ArrayList<>();
    /** This frame's lights, two vec4 each: (camera-relative centre, radius), (linear rgb * strength, unused). */
    private final float[] lights = new float[MAX_LIGHTS * 8];
    private int lightCount;
    private static final net.minecraft.resources.ResourceLocation DECALS =
            new net.minecraft.resources.ResourceLocation("bhfx", "decals");
    private static final String[] DECAL_SAMPLERS = {"DecalTex0", "DecalTex1", "DecalTex2", "DecalTex3", "DecalTex4",
            "DecalTex5", "DecalTex6", "DecalTex7"};
    /** Floats per mark as the effect set it: centre 3, size, normal 3, degrees, rgb 3, alpha, depth, cell 4, flags. */
    private static final int DECAL_RAW = 18;
    private static final int D_SIZE = 3, D_NORMAL = 4, D_DEGREES = 7, D_RGB = 8, D_ALPHA = 11, D_DEPTH = 12, D_CELL = 13,
            D_FLAGS = 17;
    private final float[] decalRaw = new float[MAX_DECALS * DECAL_RAW];
    /** What the shader reads: five vec4 per mark (see decals.fsh). */
    private final float[] decals = new float[MAX_DECALS * 20];
    private final net.deizyn.bhfx.api.client.FxTexture[] decalTextures =
            new net.deizyn.bhfx.api.client.FxTexture[MAX_DECAL_TEXTURES];
    private int decalCount;
    private int decalTextureCount;
    private final DecalCursor decalCursor = new DecalCursor();
    private final Consumer<Uniforms> decalSetup = u -> {
        for (int i = 0; i < decalTextureCount; i++) {
            u.texture(DECAL_SAMPLERS[i], decalTextures[i]);
        }
        u.floats("Decals", decals).f1("DecalCount", decalCount);
    };

    /** The mark being set; writes into {@link #decalRaw}. {@code base < 0}: the mark was dropped, calls do nothing. */
    private final class DecalCursor implements net.deizyn.bhfx.api.client.FxDecal {
        int base = -1;
        float presence = 1f;

        @Override
        public net.deizyn.bhfx.api.client.FxDecal rotation(float degrees) {
            if (base >= 0) {
                decalRaw[base + D_DEGREES] = degrees;
            }
            return this;
        }

        @Override
        public net.deizyn.bhfx.api.client.FxDecal color(int rgb, float alpha) {
            if (base >= 0) {
                float gain = brightness();
                decalRaw[base + D_RGB] = linear(rgb >> 16) * gain;
                decalRaw[base + D_RGB + 1] = linear(rgb >> 8) * gain;
                decalRaw[base + D_RGB + 2] = linear(rgb) * gain;
                decalRaw[base + D_ALPHA] = Math.max(0f, Math.min(1f, alpha)) * presence;
            }
            return this;
        }

        /** rgb holds colour * brightness; the brightness set by glow() is kept in the flags' fraction-free side slot. */
        private float brightness() {
            return glowGain;
        }

        float glowGain = 1f;

        @Override
        public net.deizyn.bhfx.api.client.FxDecal glow(float brightness) {
            if (base >= 0) {
                float gain = Math.max(0f, brightness);
                float old = glowGain <= 0f ? 1f : glowGain;
                for (int i = 0; i < 3; i++) {
                    decalRaw[base + D_RGB + i] = decalRaw[base + D_RGB + i] / old * gain;
                }
                glowGain = gain;
                decalRaw[base + D_FLAGS] = (int) decalRaw[base + D_FLAGS] | 8;
            }
            return this;
        }

        @Override
        public net.deizyn.bhfx.api.client.FxDecal mask() {
            if (base >= 0) {
                decalRaw[base + D_FLAGS] = (int) decalRaw[base + D_FLAGS] | 16;
            }
            return this;
        }

        @Override
        public net.deizyn.bhfx.api.client.FxDecal facing(Vec3 normal) {
            if (base >= 0 && normal != null && normal.lengthSqr() > 1e-8) {
                Vec3 n = normal.normalize();
                decalRaw[base + D_NORMAL] = (float) n.x;
                decalRaw[base + D_NORMAL + 1] = (float) n.y;
                decalRaw[base + D_NORMAL + 2] = (float) n.z;
            }
            return this;
        }

        @Override
        public net.deizyn.bhfx.api.client.FxDecal depth(float blocks) {
            if (base >= 0 && blocks > 0f) {
                decalRaw[base + D_DEPTH] = blocks;
            }
            return this;
        }

        @Override
        public net.deizyn.bhfx.api.client.FxDecal frame(int columns, int rows, int index) {
            if (base >= 0 && columns > 0 && rows > 0) {
                int i = Math.floorMod(index, columns * rows);
                decalRaw[base + D_CELL] = (i % columns) / (float) columns;
                decalRaw[base + D_CELL + 1] = (i / columns) / (float) rows;
                decalRaw[base + D_CELL + 2] = 1f / columns;
                decalRaw[base + D_CELL + 3] = 1f / rows;
            }
            return this;
        }
    }

    int decalCount() {
        return decalCount;
    }

    /** What the decals pass reads, valid after {@link #drawList}; for tests. */
    float[] decalData() {
        return decals;
    }

    @Override
    public net.deizyn.bhfx.api.client.FxDecal decal(net.deizyn.bhfx.api.client.FxTexture texture, Vec3 center, float size) {
        decalCursor.base = -1;
        decalCursor.glowGain = 1f;
        decalCursor.presence = presence;
        if (texture == null || center == null || !(size > 0.001f) || !(presence > 0.001f)) {
            return decalCursor;
        }
        int tex = -1;
        for (int i = 0; i < decalTextureCount; i++) {
            if (decalTextures[i].equals(texture)) {
                tex = i;
                break;
            }
        }
        if (tex < 0) {
            if (decalTextureCount >= MAX_DECAL_TEXTURES) {
                return decalCursor;
            }
            tex = decalTextureCount++;
            decalTextures[tex] = texture;
        }
        float x = (float) (center.x - camera.x);
        float y = (float) (center.y - camera.y);
        float z = (float) (center.z - camera.z);
        int slot = decalCount;
        if (slot >= MAX_DECALS) {
            // full: the new mark takes the place of the one farthest from the camera, when it is nearer than that
            float far = reachOf(x, y, z, size);
            slot = -1;
            for (int i = 0; i < MAX_DECALS; i++) {
                int b = i * DECAL_RAW;
                float r = reachOf(decalRaw[b], decalRaw[b + 1], decalRaw[b + 2], decalRaw[b + D_SIZE]);
                if (r > far) {
                    far = r;
                    slot = i;
                }
            }
            if (slot < 0) {
                return decalCursor;
            }
        } else {
            decalCount++;
        }
        int b = slot * DECAL_RAW;
        decalRaw[b] = x;
        decalRaw[b + 1] = y;
        decalRaw[b + 2] = z;
        decalRaw[b + D_SIZE] = size;
        decalRaw[b + D_NORMAL] = 0f;
        decalRaw[b + D_NORMAL + 1] = 1f;
        decalRaw[b + D_NORMAL + 2] = 0f;
        decalRaw[b + D_DEGREES] = 0f;
        decalRaw[b + D_RGB] = 1f;
        decalRaw[b + D_RGB + 1] = 1f;
        decalRaw[b + D_RGB + 2] = 1f;
        decalRaw[b + D_ALPHA] = presence;
        decalRaw[b + D_DEPTH] = 1f;
        decalRaw[b + D_CELL] = 0f;
        decalRaw[b + D_CELL + 1] = 0f;
        decalRaw[b + D_CELL + 2] = 1f;
        decalRaw[b + D_CELL + 3] = 1f;
        decalRaw[b + D_FLAGS] = tex;
        decalCursor.base = b;
        return decalCursor;
    }

    /** The built-in decals pass: packs the marks for the shader and bounds them; drawn right after the lights. */
    private Entry decalsEntry() {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < decalCount; i++) {
            int b = i * DECAL_RAW;
            int o = i * 20;
            float nx = decalRaw[b + D_NORMAL], ny = decalRaw[b + D_NORMAL + 1], nz = decalRaw[b + D_NORMAL + 2];
            // rest axes: u = up x n (east on the ground, level on a wall), v = n x u (north on the ground, up on a wall)
            float ux, uy, uz;
            if (Math.abs(ny) > 0.999f) {
                ux = 1f;
                uy = 0f;
                uz = 0f;
            } else {
                float l = (float) Math.sqrt(nz * nz + nx * nx);
                ux = nz / l;
                uy = 0f;
                uz = -nx / l;
            }
            float vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
            double a = Math.toRadians(decalRaw[b + D_DEGREES]);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            float size = decalRaw[b + D_SIZE];
            float depth = decalRaw[b + D_DEPTH];
            decals[o] = decalRaw[b];
            decals[o + 1] = decalRaw[b + 1];
            decals[o + 2] = decalRaw[b + 2];
            decals[o + 3] = size;
            decals[o + 4] = ux * cos + vx * sin;
            decals[o + 5] = uy * cos + vy * sin;
            decals[o + 6] = uz * cos + vz * sin;
            decals[o + 7] = depth;
            decals[o + 8] = vx * cos - ux * sin;
            decals[o + 9] = vy * cos - uy * sin;
            decals[o + 10] = vz * cos - uz * sin;
            decals[o + 11] = decalRaw[b + D_FLAGS];
            System.arraycopy(decalRaw, b + D_RGB, decals, o + 12, 4);
            System.arraycopy(decalRaw, b + D_CELL, decals, o + 16, 4);
            float r = (float) Math.sqrt(2f * size * size + depth * depth);
            minX = Math.min(minX, decalRaw[b] - r);
            maxX = Math.max(maxX, decalRaw[b] + r);
            minY = Math.min(minY, decalRaw[b + 1] - r);
            maxY = Math.max(maxY, decalRaw[b + 1] + r);
            minZ = Math.min(minZ, decalRaw[b + 2] - r);
            maxZ = Math.max(maxZ, decalRaw[b + 2] + r);
        }
        double hx = (maxX - minX) * 0.5, hy = (maxY - minY) * 0.5, hz = (maxZ - minZ) * 0.5;
        return new Entry(DECALS, Integer.MIN_VALUE, -1, 0.0, decalSetup, minX + hx, minY + hy, minZ + hz,
                Math.sqrt(hx * hx + hy * hy + hz * hz), null, 0f, 1f, null);
    }

    private final Consumer<Uniforms> lightSetup = u -> u.floats("Lights", lights).f1("LightCount", lightCount);
    private final List<Entry> kept = new ArrayList<>();
    private double distance;
    private float presence = 1f;
    private Vec3 camera = Vec3.ZERO;

    /** Start a frame; bounds are stored relative to {@code camera}. */
    void clear(Vec3 camera) {
        entries.clear();
        lightCount = 0;
        decalCount = 0;
        decalCursor.base = -1;
        java.util.Arrays.fill(decalTextures, 0, decalTextureCount, null);
        decalTextureCount = 0;
        this.camera = camera;
        this.presence = 1f;
    }

    void currentDistance(double distance) {
        this.distance = distance;
    }

    /** How much of the current effect shows (0..1): it fades in when built late and out when hidden. */
    void currentPresence(float presence) {
        this.presence = presence;
    }

    boolean isEmpty() {
        return entries.isEmpty() && lightCount == 0 && decalCount == 0;
    }

    int lightCount() {
        return lightCount;
    }

    @Override
    public void light(Vec3 position, int rgb, float strength, float radius) {
        float s = strength * presence;
        if (position == null || !(s > 0.001f) || !(radius > 0.01f)) {
            return;
        }
        float x = (float) (position.x - camera.x);
        float y = (float) (position.y - camera.y);
        float z = (float) (position.z - camera.z);
        int slot = lightCount;
        if (slot >= MAX_LIGHTS) {
            // full: the new light takes the place of the one farthest from the camera, when it is nearer than that
            float far = reachOf(x, y, z, radius);
            slot = -1;
            for (int i = 0; i < MAX_LIGHTS; i++) {
                float r = reachOf(lights[i * 8], lights[i * 8 + 1], lights[i * 8 + 2], lights[i * 8 + 3]);
                if (r > far) {
                    far = r;
                    slot = i;
                }
            }
            if (slot < 0) {
                return;
            }
        } else {
            lightCount++;
        }
        int b = slot * 8;
        lights[b] = x;
        lights[b + 1] = y;
        lights[b + 2] = z;
        lights[b + 3] = radius;
        lights[b + 4] = linear(rgb >> 16) * s;
        lights[b + 5] = linear(rgb >> 8) * s;
        lights[b + 6] = linear(rgb) * s;
        lights[b + 7] = 0f;
    }

    /** Distance from the camera to the nearest point a light reaches (0 inside it). */
    private static float reachOf(float x, float y, float z, float radius) {
        return Math.max(0f, (float) Math.sqrt(x * x + y * y + z * z) - radius);
    }

    private static float linear(int channel) {
        return (float) Math.pow((channel & 0xFF) / 255.0, 2.2);
    }

    /** The built-in lights pass, bounded by a sphere round every light; drawn before everything else. */
    private Entry lightsEntry() {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < lightCount; i++) {
            int b = i * 8;
            float r = lights[b + 3];
            minX = Math.min(minX, lights[b] - r);
            maxX = Math.max(maxX, lights[b] + r);
            minY = Math.min(minY, lights[b + 1] - r);
            maxY = Math.max(maxY, lights[b + 1] + r);
            minZ = Math.min(minZ, lights[b + 2] - r);
            maxZ = Math.max(maxZ, lights[b + 2] + r);
        }
        double hx = (maxX - minX) * 0.5, hy = (maxY - minY) * 0.5, hz = (maxZ - minZ) * 0.5;
        return new Entry(LIGHTS, Integer.MIN_VALUE, -1, 0.0, lightSetup, minX + hx, minY + hy, minZ + hz,
                Math.sqrt(hx * hx + hy * hy + hz * hz), null, 0f, 1f, null);
    }

    @Override
    public void add(ResourceLocation shader, Consumer<Uniforms> setup) {
        add(shader, PassOptions.DEFAULT, setup);
    }

    @Override
    public void add(ResourceLocation shader, PassOptions options, Consumer<Uniforms> setup) {
        PassOptions.Bounds b = options.bounds();
        if (b == null) {
            entries.add(new Entry(shader, options.order(), entries.size(), distance, setup, 0, 0, 0, 0, null, 0f, presence,
                    null, options.layer()));
        } else {
            Vec3 c = b.center();
            entries.add(new Entry(shader, options.order(), entries.size(), distance, setup,
                    c.x - camera.x, c.y - camera.y, c.z - camera.z, b.radius(), null, 0f, presence, null, options.layer()));
        }
    }

    @Override
    public void particles(FxParticles particles, PassOptions options, Consumer<Uniforms> setup) {
        if (!(particles instanceof ParticleBatch batch) || batch.closed()) {
            return;
        }
        float now = batch.now();
        if (batch.idleAt(now)) {
            return;
        }
        PassOptions.Bounds b = options.bounds();
        double bx = 0;
        double by = 0;
        double bz = 0;
        double radius = 0;
        if (b != null) {
            bx = b.center().x - camera.x;
            by = b.center().y - camera.y;
            bz = b.center().z - camera.z;
            radius = b.radius();
        }
        entries.add(new Entry(batch.material(), options.order(), entries.size(), distance, setup, bx, by, bz, radius,
                batch, now, presence, null));
    }

    @Override
    public void model(FxModel model, FxFrame frame, float scale, @Nullable FxPose pose, ResourceLocation material,
                      PassOptions options, Consumer<Uniforms> setup) {
        if (model == null || frame == null || material == null || !(scale > 0f)) {
            return;
        }
        Vec3 o = frame.origin();
        float[] axes = {
                (float) (frame.x().x * scale), (float) (frame.x().y * scale), (float) (frame.x().z * scale),
                (float) (frame.y().x * scale), (float) (frame.y().y * scale), (float) (frame.y().z * scale),
                (float) (frame.z().x * scale), (float) (frame.z().y * scale), (float) (frame.z().z * scale)};
        float light = 1f;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            light = level.getMaxLocalRawBrightness(BlockPos.containing(o)) / 15f;
        }
        ModelDraw draw = new ModelDraw(model, (float) (o.x - camera.x), (float) (o.y - camera.y), (float) (o.z - camera.z),
                axes, scale, light, pose == null || pose.isEmpty() ? null : pose);
        PassOptions.Bounds b = options.bounds();
        double bx = 0;
        double by = 0;
        double bz = 0;
        double radius = 0;
        if (b != null) {
            bx = b.center().x - camera.x;
            by = b.center().y - camera.y;
            bz = b.center().z - camera.z;
            radius = b.radius();
        }
        entries.add(new Entry(material, options.order(), entries.size(), distance, setup, bx, by, bz, radius, null, 0f,
                presence, draw));
    }

    @Override
    public void models(FxModel model, FxData instances, int count, @Nullable FxPose pose, ResourceLocation material,
                       PassOptions options, Consumer<Uniforms> setup) {
        if (model == null || material == null || !(instances instanceof DataTable table) || count <= 0) {
            return;
        }
        int n = Math.min(count, table.size() / 4);
        if (n <= 0) {
            return;
        }
        PassOptions.Bounds b = options.bounds();
        double bx = 0;
        double by = 0;
        double bz = 0;
        double radius = 0;
        float light = 1f;
        Vec3 lightAt = b != null ? b.center() : camera;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            light = level.getMaxLocalRawBrightness(BlockPos.containing(lightAt)) / 15f;
        }
        if (b != null) {
            bx = b.center().x - camera.x;
            by = b.center().y - camera.y;
            bz = b.center().z - camera.z;
            radius = b.radius();
        }
        ModelDraw draw = new ModelDraw(model, 0f, 0f, 0f, new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, 1f, light, table, n,
                pose == null || pose.isEmpty() ? null : pose);
        entries.add(new Entry(material, options.order(), entries.size(), distance, setup, bx, by, bz, radius, null, 0f,
                presence, draw));
    }

    @Override
    public void modelParticles(FxModel model, FxParticles pieces, float scale, @Nullable FxPose pose,
                               ResourceLocation material, PassOptions options, Consumer<Uniforms> setup) {
        if (model == null || material == null || !(scale > 0f) || !(pieces instanceof ParticleBatch batch)
                || batch.closed()) {
            return;
        }
        float now = batch.now();
        if (batch.idleAt(now)) {
            return;
        }
        PassOptions.Bounds b = options.bounds();
        double bx = 0;
        double by = 0;
        double bz = 0;
        double radius = 0;
        if (b != null) {
            bx = b.center().x - camera.x;
            by = b.center().y - camera.y;
            bz = b.center().z - camera.z;
            radius = b.radius();
        }
        float light = 1f;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            light = level.getMaxLocalRawBrightness(BlockPos.containing(batch.originX(), batch.originY(), batch.originZ())) / 15f;
        }
        ModelDraw draw = new ModelDraw(model, 0f, 0f, 0f, new float[]{scale, 0, 0, 0, scale, 0, 0, 0, scale}, scale, light,
                null, 0, pose == null || pose.isEmpty() ? null : pose, batch);
        // the batch rides along so the renderer advances its live window and counts it in the particle budget
        entries.add(new Entry(material, options.order(), entries.size(), distance, setup, bx, by, bz, radius, batch, now,
                presence, draw));
    }

    /**
     * Draw list with at most {@code max} screen passes: drops the latest-ordered, then farthest passes first. Particle
     * batches don't count (they are not full-screen passes; their per-frame slot cap is in {@code PostRenderer}), so
     * several particle effects at once don't push each other out.
     */
    List<Entry> drawList(int max) {
        kept.clear();
        kept.addAll(entries);
        int screen = 0;
        for (Entry e : entries) {
            if (!e.drawsGeometry()) {
                screen++;
            }
        }
        if (screen > max) {
            kept.sort(KEEP_ORDER);
            int[] seen = {0};
            kept.removeIf(e -> !e.drawsGeometry() && ++seen[0] > max);
        }
        kept.sort(DRAW_ORDER);
        if (decalCount > 0) {
            kept.add(0, decalsEntry()); // after the lights, before every effect's own passes
        }
        if (lightCount > 0) {
            kept.add(0, lightsEntry()); // not counted against max: one pass for every effect's lights
        }
        return kept;
    }
}
