package net.offkung.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhfx.api.FxSpawn;
import net.offkung.bhfx.api.StopMode;
import net.offkung.bhfx.impl.client.ClientFxManager;

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

    public static void shake(Vec3 source, float amount, double range) {
        ClientFxManager.shake(source, amount, range);
    }

    /** True when effects are enabled and post-processing is working on this client. */
    public static boolean postAvailable() {
        return ClientFxManager.postAvailable();
    }

    public static void reloadShaders() {
        ClientFxManager.reloadShaders();
    }
}
