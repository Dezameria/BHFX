package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.FxSpawn;
import net.deizyn.bhfx.api.StopMode;
import net.deizyn.bhfx.impl.client.ClientFxManager;

import java.util.Optional;

/** Client-side entry point. Render thread only. */
public final class BHFxClient {
    private BHFxClient() {
    }

    /** Register an effect directly (alternative to {@link RegisterFxEvent}); call during client setup. */
    public static void register(ResourceLocation id, FxFactory factory) {
        ClientFxManager.register(id, factory);
    }

    /**
     * Play an effect on this client only, with no packet — e.g. from an entity's client tick (vanilla already syncs the
     * entity to late joiners) or an Epic Fight {@code Side.CLIENT} animation event. Range and audience are ignored.
     */
    public static LocalFxHandle play(ResourceLocation fxId, FxSpawn spawn) {
        return ClientFxManager.startLocal(fxId, spawn);
    }

    public static void stop(LocalFxHandle handle, StopMode mode) {
        ClientFxManager.stopLocal(handle, mode);
    }

    /** Merge new values into a local effect's params; its {@code onParamsChanged} runs. Ignored once it has ended. */
    public static void update(LocalFxHandle handle, FxParams changed) {
        ClientFxManager.updateLocal(handle, changed);
    }

    /**
     * The running instance behind a local handle, when it is still playing and of {@code type} — for effects driven by
     * client-side cues (e.g. "a blow landed here") that do not fit in params. Do not keep it past the effect's end.
     */
    public static <T extends Fx> Optional<T> fx(LocalFxHandle handle, Class<T> type) {
        return ClientFxManager.localFx(handle, type);
    }

    /** Whether a local effect is still playing (including its ending). */
    public static boolean isAlive(LocalFxHandle handle) {
        return ClientFxManager.isLocalAlive(handle);
    }

    public static void shake(Vec3 source, float amount, double range) {
        ClientFxManager.shake(source, amount, range);
    }

    /** The player's effect settings (quality after any automatic downgrade, comfort options). */
    public static FxSettings settings() {
        return ClientFxManager.settings();
    }

    /** True when effects are enabled and post-processing is working on this client. */
    public static boolean postAvailable() {
        return ClientFxManager.postAvailable();
    }

    public static void reloadShaders() {
        ClientFxManager.reloadShaders();
    }
}
