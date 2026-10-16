package net.offkung.bhfx.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.offkung.bhfx.impl.server.ServerFx;

/**
 * Server-side entry point. Call from server code (spell logic, entity tick); bhfx sends the packets.
 * Safe on a dedicated server: nothing here touches client classes.
 */
public final class BHFx {
    private BHFx() {
    }

    /**
     * Start an effect for the spawn's audience. Returns {@link FxHandle#NONE} when bhfx is disabled on this server
     * ({@link #available(ServerLevel)} is false) — callers should keep their non-bhfx visuals (e.g. Effekseer) anyway.
     */
    public static FxHandle start(ServerLevel level, ResourceLocation fxId, FxSpawn spawn) {
        return ServerFx.start(level, fxId, spawn);
    }

    /** Stop a running effect. Unknown or finished handles are ignored. */
    public static void stop(FxHandle handle, StopMode mode) {
        ServerFx.stop(handle, mode);
    }

    /** Whether the effect is still expected to be running (by its duration). Effects with the default duration count as 10 s. */
    public static boolean isAlive(FxHandle handle) {
        return ServerFx.isAlive(handle);
    }

    /** False when the server config disables bhfx. Mods should then use their fallback visuals only. */
    public static boolean available(ServerLevel level) {
        return ServerFx.enabled();
    }
}
