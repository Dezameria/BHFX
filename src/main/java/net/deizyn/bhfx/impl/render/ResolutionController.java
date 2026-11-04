package net.deizyn.bhfx.impl.render;

/**
 * Keeps bhfx's GPU time under the budget by scaling the share of pixels effect passes use (0.25..1).
 * Drops immediately when over budget, recovers slowly, and moves in steps of 1/8 so targets are not rebuilt every frame.
 * Each step resizes the effect targets, so upward steps are also held: at most one step up every
 * {@link #RAISE_HOLD_NANOS}. A spell whose cost swings no longer makes the targets bounce between two sizes mid-cast.
 */
final class ResolutionController {
    static final double MIN = 0.25;
    static final long RAISE_HOLD_NANOS = 2_000_000_000L;
    private static final double STEP = 1.0 / 8.0;

    private double dynamic = 1.0;
    private double averageMs = -1;
    private double held = 1.0;
    private long lastChange;

    void sample(double gpuMs, double budgetMs) {
        averageMs = averageMs < 0 ? gpuMs : averageMs * 0.7 + gpuMs * 0.3;
        // GPU time scales roughly with pixel count, which is what `dynamic` scales
        double want = clamp(dynamic * budgetMs / Math.max(averageMs, 0.05));
        dynamic = want < dynamic ? want : clamp(dynamic + (want - dynamic) * 0.1);
    }

    /** A frame with nothing to draw: drift back towards full resolution. */
    void idle() {
        dynamic = Math.min(1.0, dynamic * 1.02);
    }

    /** Pixel share to use, quantised to 1/8; drops at once, rises one step at a time (see the class comment). */
    double share() {
        return share(System.nanoTime());
    }

    double share(long now) {
        double want = Math.ceil(dynamic * 8.0) / 8.0;
        if (want < held - 1e-9) {
            held = want;
            lastChange = now;
        } else if (want > held + 1e-9 && now - lastChange >= RAISE_HOLD_NANOS) {
            held = Math.min(want, held + STEP);
            lastChange = now;
        }
        return held;
    }

    /** The share last returned by {@link #share()}, without moving it. */
    double currentShare() {
        return held;
    }

    double averageMs() {
        return averageMs;
    }

    /** At the floor and still clearly over budget. */
    boolean saturated(double budgetMs) {
        return dynamic <= MIN + 1e-6 && averageMs > budgetMs * 1.25;
    }

    void reset() {
        dynamic = 1.0;
        averageMs = -1;
        held = 1.0;
        lastChange = 0L;
    }

    private static double clamp(double v) {
        return Math.max(MIN, Math.min(1.0, v));
    }
}
