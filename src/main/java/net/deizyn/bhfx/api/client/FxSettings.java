package net.offkung.bhfx.api.client;

/** The player's effect settings, read-only. Values already account for an automatic quality downgrade. */
public interface FxSettings {
    enum Quality { OFF, LOW, MEDIUM, HIGH }

    Quality quality();

    boolean bloom();

    boolean impactFrames();

    double flashScale();

    double shakeScale();

    double gpuBudgetMs();
}
