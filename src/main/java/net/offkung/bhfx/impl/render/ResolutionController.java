package net.offkung.bhfx.impl.render;

/**
 * Keeps bhfx's GPU time under the budget by scaling the share of pixels effect passes use (0.25..1).
 * Drops immediately when over budget, recovers slowly, and moves in steps of 1/8 so targets are not rebuilt every frame.
 */
final class ResolutionController {
    static final double MIN = 0.25;

    private double dynamic = 1.0;
    private double averageMs = -1;

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

    /** Pixel share to use, quantised to 1/8. */
    double share() {
        return Math.ceil(dynamic * 8.0) / 8.0;
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
    }

    private static double clamp(double v) {
        return Math.max(MIN, Math.min(1.0, v));
    }
}
