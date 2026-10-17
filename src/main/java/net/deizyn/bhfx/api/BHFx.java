package net.deizyn.bhfx.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.impl.server.ServerFx;

import java.util.Optional;

/**
 * Server-side entry point. Call from server code (spell logic, entity tick); bhfx sends the packets.
 * Safe on a dedicated server: nothing here touches client classes. Server thread only.
 */
public final class BHFx {
    private BHFx() {
    }

    /**
     * Start an effect for the spawn's audience. Returns {@link FxHandle#NONE} when bhfx is disabled on this server
     * ({@link #available(ServerLevel)} is false) or a rate limit dropped it — callers should keep their non-bhfx
     * visuals (e.g. Effekseer) anyway.
     */
    public static FxHandle start(ServerLevel level, ResourceLocation fxId, FxSpawn spawn) {
        return ServerFx.start(level, null, fxId, spawn);
    }

    /** Like {@link #start(ServerLevel, ResourceLocation, FxSpawn)}, replacing whatever is playing under {@code key}. */
    public static FxHandle start(ServerLevel level, FxKey key, ResourceLocation fxId, FxSpawn spawn) {
        return ServerFx.start(level, key, fxId, spawn);
    }

    /** Stop a running effect. Unknown or finished handles are ignored. */
    public static void stop(FxHandle handle, StopMode mode) {
        ServerFx.stop(handle, mode);
    }

    /**
     * Stop the effect playing under {@code key}, if any. Keys are server-wide, so this finds the effect whichever
     * dimension it plays in; {@code level} only has to belong to this server.
     */
    public static void stop(ServerLevel level, FxKey key, StopMode mode) {
        ServerFx.stop(key, mode);
    }

    /** The effect playing under {@code key} in any dimension of this server. */
    public static Optional<FxHandle> handle(ServerLevel level, FxKey key) {
        return ServerFx.handle(key);
    }

    /**
     * Change parameters of a running effect (merged into the current ones; the effect's {@code onParamsChanged} runs on
     * each client). Late joiners receive the merged values. Send at most a few times per second.
     */
    public static void update(FxHandle handle, FxParams changed) {
        ServerFx.update(handle, changed);
    }

    /**
     * A short screen reaction (shake, flash, vignette ...) for players within {@code range} of {@code at}, weaker the
     * farther they are. No Fx class needed. Counts towards the server's starts-per-tick limit.
     */
    public static void pulse(ServerLevel level, Vec3 at, double range, ScreenPulse pulse) {
        ServerFx.pulse(level, at, range, pulse);
    }

    /** A screen reaction for one player at full strength (e.g. the one who got hit). */
    public static void pulse(ServerPlayer player, ScreenPulse pulse) {
        ServerFx.pulse(player, pulse);
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
