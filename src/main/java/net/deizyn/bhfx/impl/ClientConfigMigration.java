package net.deizyn.bhfx.impl;

import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.impl.BHFxConfig.Quality;

/**
 * One-time updates of an existing bhfx-client.toml. Forge keeps the values already in a file when a default changes,
 * so a new default only reaches old installs through here. Runs when the client config loads.
 */
public final class ClientConfigMigration {
    /** Raise when adding a step to {@link #run()}. */
    static final int CURRENT = 3;

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
        // 2 (0.3.5): offWithShaderpack defaults to false; true was the old default, so a file that still has it moves
        if (version < 2 && BHFxConfig.OFF_WITH_SHADERPACK.get()) {
            BHFxConfig.OFF_WITH_SHADERPACK.set(false);
            BHFxMod.LOGGER.info("bhfx: compat.offWithShaderpack moved to the new default (false): post effects stay on "
                    + "with a shader pack. Turn \"Off with shader pack\" on in Mods > bhfx > Config to go back.");
        }
        // 3 (0.3.5): mirror defaults distance 32 -> 16 and maxPlanes 4 -> 6 (a closed mirror room shows up to five
        // surfaces at once). Each value moves only if it is still the old default.
        if (version < 3) {
            if (BHFxConfig.MIRROR_DISTANCE.get() == 32) {
                BHFxConfig.MIRROR_DISTANCE.set(16);
            }
            if (BHFxConfig.MIRROR_MAX_PLANES.get() == 4) {
                BHFxConfig.MIRROR_MAX_PLANES.set(6);
            }
        }
        BHFxConfig.SETTINGS_VERSION.set(CURRENT);
        BHFxConfig.CLIENT_SPEC.save();
    }

    /** The defaults before 0.3.3: quality HIGH, 2.3 MP cap, 6 ms budget. */
    static boolean hasOldDefaults(Quality quality, double megapixels, double budgetMs) {
        return quality == Quality.HIGH && Math.abs(megapixels - 2.3) < 1e-6 && Math.abs(budgetMs - 6.0) < 1e-6;
    }
}
