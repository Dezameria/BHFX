package net.deizyn.bhfx.impl;

import net.deizyn.bhfx.impl.BHFxConfig.Quality;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigMigrationTest {
    @Test
    void onlyTheExactOldDefaultsMove() {
        assertTrue(ClientConfigMigration.hasOldDefaults(Quality.HIGH, 2.3, 6.0));
        // anything the player changed stays
        assertFalse(ClientConfigMigration.hasOldDefaults(Quality.HIGH, 2.3, 8.0));
        assertFalse(ClientConfigMigration.hasOldDefaults(Quality.HIGH, 4.0, 6.0));
        assertFalse(ClientConfigMigration.hasOldDefaults(Quality.LOW, 2.3, 6.0));
        // already the new defaults
        assertFalse(ClientConfigMigration.hasOldDefaults(Quality.MEDIUM, 1.6, 4.0));
    }

    @Test
    void migratedFileMatchesNewDefaults() {
        assertEquals(Quality.MEDIUM, BHFxConfig.QUALITY.getDefault());
        assertEquals(1.6, BHFxConfig.MAX_FX_MEGAPIXELS.getDefault());
        assertEquals(4.0, BHFxConfig.GPU_BUDGET_MS.getDefault());
        assertEquals(0, BHFxConfig.SETTINGS_VERSION.getDefault());
    }
}
