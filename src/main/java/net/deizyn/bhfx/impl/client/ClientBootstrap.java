package net.offkung.bhfx.impl.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.offkung.bhfx.BHFxMod;
import net.offkung.bhfx.api.client.RegisterFxEvent;
import net.offkung.bhfx.impl.client.test.TestFx;

/** Client-only wiring. Loaded through DistExecutor so a dedicated server never touches it. */
public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientBootstrap::clientSetup);
        modBus.addListener(ClientBootstrap::registerReloadListeners);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientFxManager.register(BHFxMod.id("test"), TestFx::new);
            ModLoader.get().postEvent(new RegisterFxEvent());
        });
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        // F3+T: drop compiled shaders so edited .fsh / .glsl files take effect
        event.registerReloadListener((ResourceManagerReloadListener) manager -> ClientFxManager.reloadShaders());
    }
}
