package net.offkung.bhfx.impl.client;

import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.offkung.bhfx.impl.BHFxConfig;

/** Forge-bus hooks. bhfx needs no mixins: it renders at {@code AFTER_LEVEL}, after the world and before the hand. */
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientFxManager.tick(Minecraft.getInstance());
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY || stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            // these stages carry the camera-rotation pose stack (AFTER_LEVEL carries the projection stack instead)
            ClientFxManager.captureMatrices(event.getPoseStack().last().pose(), event.getProjectionMatrix());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ClientFxManager.render(event.getCamera(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float amount = (float) (ClientFxManager.shakeAmount() * BHFxConfig.SHAKE_SCALE.get());
        if (amount < 1e-4f) {
            return;
        }
        double t = (System.nanoTime() % 1_000_000_000_000L) / 1.0e9;
        event.setYaw(event.getYaw() + CameraNoise.sample(t * 11.0, 0.0) * 2.5f * amount);
        event.setPitch(event.getPitch() + CameraNoise.sample(t * 11.0, 37.0) * 2.0f * amount);
        event.setRoll(event.getRoll() + CameraNoise.sample(t * 7.0, 91.0) * 2.8f * amount);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ClientFxManager.renderHud(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFxManager.clear();
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bhfx")
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(ClientFxManager.status()), false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    ClientFxManager.reloadShaders();
                    ctx.getSource().sendSuccess(() -> Component.literal("bhfx: shaders will recompile on next use"), false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ClientFxManager.setRuntimeOff(true);
                    ctx.getSource().sendSuccess(() -> Component.literal("bhfx: off for this session (no effects, no GPU work)"), false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("on").executes(ctx -> {
                    ClientFxManager.setRuntimeOff(false);
                    ctx.getSource().sendSuccess(() -> Component.literal("bhfx: on"), false);
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
