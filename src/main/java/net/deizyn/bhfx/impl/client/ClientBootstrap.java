package net.deizyn.bhfx.impl.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.RegisterFxEvent;
import net.deizyn.bhfx.impl.client.test.TestFx;
import net.deizyn.bhfx.impl.client.test.TrackerTestFx;

import java.io.IOException;

/** Client-only wiring. Loaded through DistExecutor so a dedicated server never touches it. */
public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientBootstrap::clientSetup);
        modBus.addListener(ClientBootstrap::registerReloadListeners);
        modBus.addListener(ClientBootstrap::registerShaders);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        // Mods menu → bhfx → Config
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new BHFxSettingsScreen(parent)));
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientFxManager.register(BHFxMod.id("test"), TestFx::new);
            ClientFxManager.register(BHFxMod.id("test_tracker"), TrackerTestFx::new);
            ModLoader.get().postEvent(new RegisterFxEvent());
        });
    }

    private static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), BHFxMod.id("mirror_composite"),
                    DefaultVertexFormat.POSITION), shader -> MirrorRenderer.composite = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), BHFxMod.id("mirror_area"),
                    DefaultVertexFormat.POSITION), shader -> MirrorRenderer.area = shader);
        } catch (IOException e) {
            BHFxMod.LOGGER.error("bhfx: mirror shader failed to load; mirrors will show no reflection", e);
        }
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        // F3+T: drop compiled shaders so edited .fsh / .glsl files take effect
        event.registerReloadListener((ResourceManagerReloadListener) manager -> ClientFxManager.reloadShaders());
    }
}
