package net.deizyn.bhfx.impl.render;

import net.deizyn.bhfx.api.client.FxPose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoneRigTest {
    private static final int F = GeoModelLoader.FLOATS;

    /** root (a cube) → arm (pivot 0 8 0, rotation {@code armRot}, a cube) → hand (pivot 0 16 0, a cube). */
    private static String geo(String armRot) {
        return "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.t\","
                + "\"texture_width\":64,\"texture_height\":64},\"bones\":["
                + "{\"name\":\"root\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[-2,0,-2],\"size\":[4,8,4],\"uv\":[0,0]}]},"
                + "{\"name\":\"arm\",\"parent\":\"root\",\"pivot\":[0,8,0],\"rotation\":" + armRot
                + ",\"cubes\":[{\"origin\":[-1,8,-1],\"size\":[2,8,2],\"uv\":[0,16]}]},"
                + "{\"name\":\"hand\",\"parent\":\"arm\",\"pivot\":[0,16,0],"
                + "\"cubes\":[{\"origin\":[-1.5,16,-1.5],\"size\":[3,3,3],\"uv\":[0,32],\"pivot\":[0,17,0],\"rotation\":[0,0,30]}]}"
                + "]}]}";
    }

    /** Every vertex of {@code rest} put through its bone's matrix, as the vertex shader does. */
    private static float[] posed(GeoModelLoader.Data rest, float[] skin, int bones) {
        float[] v = rest.vertices().clone();
        for (int i = 0; i < rest.vertexCount(); i++) {
            int o = i * F;
            int b = (int) v[o + 8];
            if (b >= bones) {
                continue;
            }
            int m = b * 12;
            float x = v[o];
            float y = v[o + 1];
            float z = v[o + 2];
            v[o] = skin[m] * x + skin[m + 1] * y + skin[m + 2] * z + skin[m + 3];
            v[o + 1] = skin[m + 4] * x + skin[m + 5] * y + skin[m + 6] * z + skin[m + 7];
            v[o + 2] = skin[m + 8] * x + skin[m + 9] * y + skin[m + 10] * z + skin[m + 11];
        }
        return v;
    }

    private static void samePositions(GeoModelLoader.Data expected, float[] actual) {
        float[] e = expected.vertices();
        for (int i = 0; i < expected.vertexCount(); i++) {
            for (int k = 0; k < 3; k++) {
                assertEquals(e[i * F + k], actual[i * F + k], 1e-4f, "vertex " + i + " axis " + k);
            }
        }
    }

    @Test
    void emptyPoseMovesNothing() {
        BoneRig rig = BoneRig.of(GeoModelLoader.load(geo("[0,0,0]")));
        float[] skin = rig.solve(new FxPose());
        assertEquals(0, rig.moved());
        assertEquals(3, rig.count());
        for (int b = 0; b < 3; b++) {
            assertEquals(1f, skin[b * 12]);
            assertEquals(1f, skin[b * 12 + 5]);
            assertEquals(1f, skin[b * 12 + 10]);
            assertEquals(0f, skin[b * 12 + 3]);
        }
    }

    @Test
    void poseRotationMatchesTheModelBakedWithThatRotation() {
        // arm modelled at 10, 0, 0 and posed by 15, 25, -40 = arm modelled at 25, 25, -40 (hand follows)
        GeoModelLoader.Data rest = GeoModelLoader.load(geo("[10,0,0]"));
        GeoModelLoader.Data target = GeoModelLoader.load(geo("[25,25,-40]"));
        BoneRig rig = BoneRig.of(rest);
        float[] skin = rig.solve(new FxPose().rotation("arm", 15, 25, -40));
        assertEquals(2, rig.moved());
        samePositions(target, posed(rest, skin, rig.count()));
    }

    @Test
    void positionMovesTheBoneAndItsChildrenInPixels() {
        GeoModelLoader.Data rest = GeoModelLoader.load(geo("[0,0,0]"));
        BoneRig rig = BoneRig.of(rest);
        float[] skin = rig.solve(new FxPose().position("arm", 16, 8, 0));
        // GeckoLib mirrors x: +16 px is one block toward -x
        assertEquals(-1f, skin[12 + 3], 1e-6f);
        assertEquals(0.5f, skin[12 + 7], 1e-6f);
        assertEquals(-1f, skin[24 + 3], 1e-6f);
        assertEquals(0f, skin[3], 0f); // root untouched
    }

    @Test
    void hiddenBoneCollapsesWithItsChildren() {
        GeoModelLoader.Data rest = GeoModelLoader.load(geo("[0,0,0]"));
        BoneRig rig = BoneRig.of(rest);
        float[] v = posed(rest, rig.solve(new FxPose().hide("arm")), rig.count());
        for (int i = 0; i < rest.vertexCount(); i++) {
            int b = (int) v[i * F + 8];
            if (b >= 1) { // arm and hand: all at the arm's pivot (0, 0.5, 0)
                assertEquals(0f, v[i * F], 1e-6f);
                assertEquals(0.5f, v[i * F + 1], 1e-6f);
                assertEquals(0f, v[i * F + 2], 1e-6f);
            }
        }
    }

    @Test
    void keepsTheResultWhileThePoseIsUnchanged() {
        BoneRig rig = BoneRig.of(GeoModelLoader.load(geo("[0,0,0]")));
        FxPose pose = new FxPose().rotation("hand", 0, 90, 0);
        float[] a = rig.solve(pose);
        float before = a[24];
        assertSame(a, rig.solve(pose));
        pose.rotation("hand", 0, 0, 0);
        rig.solve(pose);
        assertEquals(1f, a[24], 1e-6f);
        assertTrue(Math.abs(before) < 1e-6f);
        pose.clear();
        rig.solve(pose);
        assertEquals(0, rig.moved());
    }

    @Test
    void reachHoldsEveryPosedVertex() {
        GeoModelLoader.Data rest = GeoModelLoader.load(geo("[0,0,0]"));
        BoneRig rig = BoneRig.of(rest);
        float[] skin = rig.solve(new FxPose().rotation("arm", 0, 0, 120).scale("hand", 2f).position("arm", 5, 3, 0));
        float cx = (rest.min()[0] + rest.max()[0]) * 0.5f;
        float cy = (rest.min()[1] + rest.max()[1]) * 0.5f;
        float cz = (rest.min()[2] + rest.max()[2]) * 0.5f;
        float r0 = 0f;
        for (int i = 0; i < rest.vertexCount(); i++) {
            float[] v = rest.vertices();
            r0 = Math.max(r0, dist(v, i, cx, cy, cz));
        }
        float reach = rig.reach(cx, cy, cz, r0);
        float[] p = posed(rest, skin, rig.count());
        for (int i = 0; i < rest.vertexCount(); i++) {
            assertTrue(dist(p, i, cx, cy, cz) <= reach + 1e-4f, "vertex " + i + " outside the grown bounds");
        }
    }

    @Test
    void unknownBonesAndParentLoopsAreHarmless() {
        BoneRig rig = new BoneRig(new String[]{"a", "b", "c"}, new int[]{1, 0, -1}, new float[9], new float[9]);
        float[] skin = rig.solve(new FxPose().rotation("a", 0, 90, 0).rotation("nope", 1, 2, 3).rotation("c", 0, 0, 90)
                .position("b", Float.NaN, 0, 0));
        assertEquals(1, rig.moved()); // only c: a and b loop, so they stay put
        assertEquals(1f, skin[0]);
        assertEquals(1f, skin[12]);
        assertEquals(0f, skin[24], 1e-6f);
    }

    private static float dist(float[] v, int i, float cx, float cy, float cz) {
        float dx = v[i * F] - cx;
        float dy = v[i * F + 1] - cy;
        float dz = v[i * F + 2] - cz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
