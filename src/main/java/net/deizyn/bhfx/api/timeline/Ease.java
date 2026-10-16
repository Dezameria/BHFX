package net.deizyn.bhfx.api.timeline;

/** Small easing helpers for envelopes. All inputs outside their range are clamped. */
public final class Ease {
    private Ease() {
    }

    public static float clamp01(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    /** Cubic smoothstep of x in [0, 1]. */
    public static float smooth(float x) {
        x = clamp01(x);
        return x * x * (3f - 2f * x);
    }

    /** Quintic smootherstep of x in [0, 1]. */
    public static float smoother(float x) {
        x = clamp01(x);
        return x * x * x * (x * (x * 6f - 15f) + 10f);
    }

    /** 0 before in0, eases to 1 by in1, holds, eases back to 0 between out0 and out1. */
    public static float window(float t, float in0, float in1, float out0, float out1) {
        float up = smooth((t - in0) / Math.max(in1 - in0, 1e-3f));
        float down = smooth((t - out0) / Math.max(out1 - out0, 1e-3f));
        return up * (1f - down);
    }

    /** 1 - e^(-k x): fast start, slow approach to 1. */
    public static float expOut(float x, float k) {
        return 1f - (float) Math.exp(-Math.max(x, 0f) * k);
    }
}
