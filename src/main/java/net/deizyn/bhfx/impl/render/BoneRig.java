package net.deizyn.bhfx.impl.render;

import net.deizyn.bhfx.api.client.FxPose;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * A baked model's bones, for posing it on the GPU: {@link #solve} turns an {@link FxPose} into one 3x4 matrix per bone
 * (rows, 12 floats) that takes a baked (rest-pose) vertex to where the pose puts it. Each bone's matrix is built the way
 * GeckoLib's {@code RenderUtils.prepMatrixForBone} does (move by position, to the pivot, turn Z Y X by rest + pose
 * rotation, scale, back from the pivot, after the parent's), times the inverse of the same matrix at rest. Bones neither
 * the pose nor an ancestor touch are left as exact identities. Nothing is allocated per call; the last result is kept
 * while the pose doesn't change.
 */
public final class BoneRig {
    private final String[] names;
    private final int[] parents;
    private final float[] pivots;
    private final float[] restDegrees;
    /** Parents before children; bones in a parent loop are left out (they stay at rest). */
    private final int[] order;
    private final Matrix4f[] restInverse;
    private final Matrix4f[] world;
    private final boolean[] moved;
    private final int count;
    private final float[] skin;
    private final Matrix4f scratch = new Matrix4f();

    @Nullable
    private FxPose lastPose;
    private int lastVersion;
    private int lastMoved;

    public BoneRig(String[] names, int[] parents, float[] pivots, float[] restDegrees) {
        this.names = names;
        this.parents = parents;
        this.pivots = pivots;
        this.restDegrees = restDegrees;
        int b = names.length;
        this.count = Math.min(b, FxPose.MAX_BONES);
        this.order = order(parents);
        this.world = new Matrix4f[b];
        this.restInverse = new Matrix4f[b];
        this.moved = new boolean[b];
        this.skin = new float[count * 12];
        for (int i = 0; i < b; i++) {
            world[i] = new Matrix4f();
            restInverse[i] = new Matrix4f();
        }
        for (int i : order) {
            int p = parents[i];
            local(p >= 0 ? world[p] : null, i, null, world[i]);
            restInverse[i].set(world[i]).invert();
        }
        resetSkin();
    }

    public static BoneRig of(GeoModelLoader.Data d) {
        return new BoneRig(d.boneNames(), d.boneParents(), d.bonePivots(), d.boneRotations());
    }

    /** Bones that can move (and matrices {@link #solve} fills): at most {@link FxPose#MAX_BONES}. */
    public int count() {
        return count;
    }

    /**
     * Matrices for {@code pose}: 12 floats per bone (three rows of a 3x4), {@link #count()} bones. The array is reused;
     * read it before the next call.
     */
    public float[] solve(FxPose pose) {
        if (pose == lastPose && pose.version() == lastVersion) {
            return skin;
        }
        lastPose = pose;
        lastVersion = pose.version();
        lastMoved = 0;
        for (int i : order) {
            int p = parents[i];
            boolean parentMoved = p >= 0 && moved[p];
            float[] c = pose.channels(names[i]);
            if (c != null && !finite(c)) {
                c = null;
            }
            if (c == null && !parentMoved) {
                moved[i] = false;
                local(p >= 0 ? world[p] : null, i, null, world[i]);
                if (i < count) {
                    identity(i);
                }
                continue;
            }
            moved[i] = true;
            local(p >= 0 ? world[p] : null, i, c, world[i]);
            if (i < count) {
                lastMoved++;
                scratch.set(world[i]).mul(restInverse[i]);
                write(i, scratch);
            }
        }
        return skin;
    }

    /** Bones the last {@link #solve} moved (0: the pose changed nothing and the model can draw as baked). */
    public int moved() {
        return lastMoved;
    }

    /**
     * A sphere sure to hold the posed model, given one ({@code cx, cy, cz, radius}, model space) that holds it at
     * rest: the radius grows by how far each moved bone carries the centre and how much it stretches. For the last
     * {@link #solve}.
     */
    public float reach(float cx, float cy, float cz, float radius) {
        float best = radius;
        for (int i = 0; i < count; i++) {
            int o = i * 12;
            if (skin[o] == 1f && skin[o + 5] == 1f && skin[o + 10] == 1f && skin[o + 3] == 0f && skin[o + 7] == 0f
                    && skin[o + 11] == 0f && skin[o + 1] == 0f && skin[o + 2] == 0f && skin[o + 4] == 0f
                    && skin[o + 6] == 0f && skin[o + 8] == 0f && skin[o + 9] == 0f) {
                continue;
            }
            float px = skin[o] * cx + skin[o + 1] * cy + skin[o + 2] * cz + skin[o + 3];
            float py = skin[o + 4] * cx + skin[o + 5] * cy + skin[o + 6] * cz + skin[o + 7];
            float pz = skin[o + 8] * cx + skin[o + 9] * cy + skin[o + 10] * cz + skin[o + 11];
            float shift = (float) Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy) + (pz - cz) * (pz - cz));
            float stretch = 0f;
            for (int col = 0; col < 3; col++) {
                float a = skin[o + col];
                float b = skin[o + 4 + col];
                float c = skin[o + 8 + col];
                stretch = Math.max(stretch, (float) Math.sqrt(a * a + b * b + c * c));
            }
            best = Math.max(best, shift + stretch * radius);
        }
        return best;
    }

    /** {@code out} = parent (or identity) × this bone's own transform, with pose channels {@code c} (null: at rest). */
    private void local(@Nullable Matrix4f parent, int i, @Nullable float[] c, Matrix4f out) {
        if (parent != null) {
            out.set(parent);
        } else {
            out.identity();
        }
        if (c != null && (c[3] != 0f || c[4] != 0f || c[5] != 0f)) {
            out.translate(-c[3] / 16f, c[4] / 16f, c[5] / 16f);
        }
        float px = pivots[i * 3];
        float py = pivots[i * 3 + 1];
        float pz = pivots[i * 3 + 2];
        out.translate(px, py, pz);
        float rx = restDegrees[i * 3] + (c != null ? c[0] : 0f);
        float ry = restDegrees[i * 3 + 1] + (c != null ? c[1] : 0f);
        float rz = restDegrees[i * 3 + 2] + (c != null ? c[2] : 0f);
        if (rz != 0f) {
            out.rotateZ((float) Math.toRadians(rz));
        }
        if (ry != 0f) {
            out.rotateY((float) Math.toRadians(-ry));
        }
        if (rx != 0f) {
            out.rotateX((float) Math.toRadians(-rx));
        }
        if (c != null && (c[6] != 1f || c[7] != 1f || c[8] != 1f)) {
            out.scale(c[6], c[7], c[8]);
        }
        out.translate(-px, -py, -pz);
    }

    private void write(int i, Matrix4f m) {
        int o = i * 12;
        skin[o] = m.m00();
        skin[o + 1] = m.m10();
        skin[o + 2] = m.m20();
        skin[o + 3] = m.m30();
        skin[o + 4] = m.m01();
        skin[o + 5] = m.m11();
        skin[o + 6] = m.m21();
        skin[o + 7] = m.m31();
        skin[o + 8] = m.m02();
        skin[o + 9] = m.m12();
        skin[o + 10] = m.m22();
        skin[o + 11] = m.m32();
    }

    private void identity(int i) {
        int o = i * 12;
        for (int k = 0; k < 12; k++) {
            skin[o + k] = 0f;
        }
        skin[o] = 1f;
        skin[o + 5] = 1f;
        skin[o + 10] = 1f;
    }

    private void resetSkin() {
        for (int i = 0; i < count; i++) {
            identity(i);
        }
    }

    private static boolean finite(float[] c) {
        for (float v : c) {
            if (!Float.isFinite(v)) {
                return false;
            }
        }
        return true;
    }

    private static int[] order(int[] parents) {
        int b = parents.length;
        int[] state = new int[b]; // 0 new, 1 visiting, 2 placed, 3 in a loop
        int[] out = new int[b];
        int n = 0;
        for (int i = 0; i < b; i++) {
            n = visit(i, parents, state, out, n);
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private static int visit(int i, int[] parents, int[] state, int[] out, int n) {
        // walk up to the first placed (or missing) ancestor, then place the chain top-down
        int[] chain = new int[parents.length];
        int len = 0;
        int at = i;
        while (at >= 0 && at < parents.length && state[at] == 0) {
            state[at] = 1;
            chain[len++] = at;
            at = parents[at];
        }
        boolean loop = at >= 0 && at < parents.length && (state[at] == 1 || state[at] == 3);
        for (int k = len - 1; k >= 0; k--) {
            if (loop) {
                state[chain[k]] = 3;
            } else {
                state[chain[k]] = 2;
                out[n++] = chain[k];
            }
        }
        return n;
    }
}
