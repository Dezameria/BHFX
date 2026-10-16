package net.offkung.bhfx.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Server-side reference to a started effect, used to stop it later. */
public record FxHandle(ResourceKey<Level> dimension, int id) {
    /** Returned when nothing was started (bhfx disabled on the server). Stopping it does nothing. */
    public static final FxHandle NONE = new FxHandle(Level.OVERWORLD, 0);

    public boolean isNone() {
        return id == 0;
    }
}
