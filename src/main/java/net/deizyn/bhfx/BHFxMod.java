package net.deizyn.bhfx;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.deizyn.bhfx.impl.BHFxConfig;
import net.deizyn.bhfx.impl.ClientConfigMigration;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.deizyn.bhfx.impl.net.BHFxNetwork;
import net.deizyn.bhfx.impl.server.ServerFxEvents;
import org.slf4j.Logger;

@Mod(BHFxMod.MODID)
public final class BHFxMod {
    public static final String MODID = "bhfx";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BHFxMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BHFxConfig.SERVER_SPEC, "bhfx-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BHFxConfig.CLIENT_SPEC, "bhfx-client.toml");
        modBus.addListener(this::commonSetup);
        modBus.addListener(BHFxMod::configLoaded);
        MinecraftForge.EVENT_BUS.register(ServerFxEvents.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.deizyn.bhfx.impl.client.ClientBootstrap.init(modBus));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(BHFxNetwork::register);
    }

    private static void configLoaded(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == BHFxConfig.CLIENT_SPEC) {
            ClientConfigMigration.run();
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
