package net.deizyn.bhfx.impl.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Screen area of a pass, in uv (0..1, origin bottom-left like GL window coordinates). {@link #FULL} = the whole
 * screen, {@link #NONE} = nothing on screen.
 */
public record ScreenRect(float minU, float minV, float maxU, float maxV) {
    public static final ScreenRect FULL = new ScreenRect(0f, 0f, 1f, 1f);
    public static final ScreenRect NONE = new ScreenRect(0f, 0f, 0f, 0f);
    /** A rect covering more than this share of the screen is drawn as FULL (the extra copy would cost more). */
    static final float FULL_SHARE = 0.85f;
    private static final float NEAR_W = 1e-3f;

    public boolean empty() {
        return maxU <= minU || maxV <= minV;
    }

    public boolean full() {
        return (maxU - minU) * (maxV - minV) >= FULL_SHARE;
    }

    /**
     * Conservative screen area of a sphere at camera-relative {@code (x, y, z)}: the 8 corners of the cube around it,
     * projected. Any corner at or behind the camera plane gives {@link #FULL}, since the projection is then unbounded;
     * all corners behind it, or a rect wholly off screen, give {@link #NONE}.
     */
    public static ScreenRect ofSphere(Matrix4f viewProj, double x, double y, double z, double radius, Vector4f scratch) {
        if (!(radius > 0)) {
            return FULL;
        }
        float minU = Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        int behind = 0;
        for (int i = 0; i < 8; i++) {
            scratch.set((float) (x + ((i & 1) == 0 ? -radius : radius)),
                    (float) (y + ((i & 2) == 0 ? -radius : radius)),
                    (float) (z + ((i & 4) == 0 ? -radius : radius)), 1f);
            viewProj.transform(scratch);
            if (scratch.w <= NEAR_W) {
                behind++;
                continue;
            }
            float u = scratch.x / scratch.w * 0.5f + 0.5f;
            float v = scratch.y / scratch.w * 0.5f + 0.5f;
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
        }
        if (behind == 8) {
            return NONE;
        }
        if (behind > 0) {
            return FULL;
        }
        ScreenRect r = new ScreenRect(Math.max(minU, 0f), Math.max(minV, 0f), Math.min(maxU, 1f), Math.min(maxV, 1f));
        return r.empty() ? NONE : r;
    }
}
