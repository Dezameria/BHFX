package net.deizyn.bhfx.api.client;

/** The player's effect settings, read-only. Values already account for an automatic quality downgrade. */
public interface FxSettings {
    enum Quality { OFF, LOW, MEDIUM, HIGH }

    /** Quality in use, after any automatic downgrade this session. */
    Quality quality();

    /** Whether bloom is drawn (off at LOW, or when the player turned it off). */
    boolean bloom();

    /** Whether impact frames are shown; {@link ScreenParams#impact} is ignored when false. */
    boolean impactFrames();

    /** The player's multiplier for flashes, 0..1, already applied by bhfx. */
    double flashScale();

    /** The player's multiplier for camera shake, 0..1, already applied by bhfx. */
    double shakeScale();

    /** GPU time per frame bhfx aims to stay under; above it resolution drops, then quality. */
    double gpuBudgetMs();
}
