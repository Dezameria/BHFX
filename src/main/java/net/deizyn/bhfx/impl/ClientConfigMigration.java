package net.deizyn.bhfx.impl;

import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.impl.BHFxConfig.Quality;

/**
 * One-time updates of an existing bhfx-client.toml. Forge keeps the values already in a file when a default changes,
 * so a new default only reaches old installs through here. Runs when the client config loads.
 */
public final class ClientConfigMigration {
    /** Raise when adding a step to {@link #run()}. */
    static final int CURRENT = 1;

    private ClientConfigMigration() {
    }

    public static void run() {
        int version = BHFxConfig.SETTINGS_VERSION.get();
        if (version >= CURRENT) {
            return;
        }
        // 1 (0.3.3): defaults became the Medium preset. Only a file still on the old defaults moves; anything the
        // player changed by hand stays as it is.
        if (version < 1 && hasOldDefaults(BHFxConfig.QUALITY.get(), BHFxConfig.MAX_FX_MEGAPIXELS.get(),
                BHFxConfig.GPU_BUDGET_MS.get())) {
            BHFxConfig.QUALITY.set(Quality.MEDIUM);
            BHFxConfig.MAX_FX_MEGAPIXELS.set(1.6);
            BHFxConfig.GPU_BUDGET_MS.set(4.0);
            BHFxMod.LOGGER.info("bhfx: bhfx-client.toml had the old default settings (HIGH, 2.3 MP, 6 ms); "
                    + "moved to the new defaults (MEDIUM, 1.6 MP, 4 ms). Pick High in Mods > bhfx > Config to go back.");
        }
        BHFxConfig.SETTINGS_VERSION.set(CURRENT);
        BHFxConfig.CLIENT_SPEC.save();
    }

    /** The defaults before 0.3.3: quality HIGH, 2.3 MP cap, 6 ms budget. */
    static boolean hasOldDefaults(Quality quality, double megapixels, double budgetMs) {
        return quality == Quality.HIGH && Math.abs(megapixels - 2.3) < 1e-6 && Math.abs(budgetMs - 6.0) < 1e-6;
    }
}
