package net.deizyn.bhfx.impl.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.deizyn.bhfx.impl.render.AnimationLibrary;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.RegisterFxEvent;
import net.deizyn.bhfx.impl.client.test.FlowTestFx;
import net.deizyn.bhfx.impl.client.test.ParticlesTestFx;
import net.deizyn.bhfx.impl.client.test.DataTestFx;
import net.deizyn.bhfx.impl.client.test.ModelTestFx;
import net.deizyn.bhfx.impl.client.test.PointsTestFx;
import net.deizyn.bhfx.impl.client.test.SdfTestFx;
import net.deizyn.bhfx.impl.client.test.WaveTestFx;
import net.deizyn.bhfx.impl.client.test.SwirlTestFx;
import net.deizyn.bhfx.impl.client.test.VortexTestFx;
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
        modBus.addListener(ClientBootstrap::addLayers);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        // Mods menu → bhfx → Config
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new BHFxSettingsScreen(parent)));
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientFxManager.register(BHFxMod.id("test"), TestFx::new);
            ClientFxManager.register(BHFxMod.id("test_tracker"), TrackerTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_particles"), ParticlesTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_vortex"), VortexTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_flow"), FlowTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_swirl"), SwirlTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_data"), DataTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_lights"), net.deizyn.bhfx.impl.client.test.LightsTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_decals"), net.deizyn.bhfx.impl.client.test.DecalsTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_sdf"), SdfTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_points"), PointsTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_model"), ModelTestFx::new);
            ClientFxManager.register(BHFxMod.id("test_wave"), WaveTestFx::new);
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

    /** Body part capture ({@link BodyPartLayer}) on every living-entity renderer, players' skins included. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        int added = 0;
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            EntityRenderer<?> renderer;
            try {
                renderer = event.getRenderer((EntityType) type);
            } catch (ClassCastException notLiving) {
                continue; // getRenderer casts to LivingEntityRenderer
            }
            if (renderer instanceof LivingEntityRenderer living) {
                living.addLayer(new BodyPartLayer<>(living));
                added++;
            }
        }
        for (String skin : event.getSkins()) {
            LivingEntityRenderer living = event.getSkin(skin);
            if (living != null) {
                living.addLayer(new BodyPartLayer<>(living));
                added++;
            }
        }
        BHFxMod.LOGGER.debug("bhfx: body part capture on {} renderers", added);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        // F3+T: drop compiled shaders so edited .fsh / .glsl files take effect
        event.registerReloadListener((ResourceManagerReloadListener) manager -> ClientFxManager.reloadShaders());
        // and re-read model animations (FxAnimation)
        event.registerReloadListener((ResourceManagerReloadListener) manager -> AnimationLibrary.clear());
    }
}
