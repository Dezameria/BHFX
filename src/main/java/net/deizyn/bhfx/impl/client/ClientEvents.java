package net.deizyn.bhfx.impl.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.deizyn.bhfx.api.client.CameraPose;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.impl.BHFxConfig;

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
        CameraParamsImpl cam = ClientFxManager.camera();
        if (!cam.idle()) {
            CameraPose pose = cam.pose();
            if (pose != null) {
                float w = cam.poseWeight();
                event.setYaw(event.getYaw() + Mth.wrapDegrees(pose.yaw() - event.getYaw()) * w);
                event.setPitch(Mth.lerp(w, event.getPitch(), pose.pitch()));
                event.setRoll(Mth.lerp(w, event.getRoll(), pose.roll()));
                Camera camera = event.getCamera();
                CameraMover.move(camera, camera.getPosition().lerp(pose.position(), w));
            }
            event.setRoll(event.getRoll() + cam.rollDegrees());
        }
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
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        // only the world view; the hand keeps its own FOV so it does not stretch
        float mul = ClientFxManager.camera().fovMultiplier();
        if (event.usedConfiguredFov() && mul != 1f) {
            event.setFOV(event.getFOV() * mul);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (ClientFxManager.camera().hideHandRequested()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (ClientFxManager.camera().hideHudRequested() && !event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ClientFxManager.renderHud(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (Minecraft.getInstance().options.renderDebug) {
            ClientFxManager.debugLines(event.getLeft());
        }
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
                .then(Commands.literal("stats").executes(ctx -> {
                    for (String line : ClientFxManager.statsLines()) {
                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("bench")
                        .executes(ctx -> bench(ctx, BHFxMod.id("test"), 10))
                        .then(Commands.argument("fx", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ClientFxManager.registeredIds(), builder))
                                .executes(ctx -> bench(ctx, ResourceLocationArgument.getId(ctx, "fx"), 10))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(4, 60))
                                        .executes(ctx -> bench(ctx, ResourceLocationArgument.getId(ctx, "fx"),
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))))
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

    private static int bench(CommandContext<CommandSourceStack> ctx, ResourceLocation fx, int seconds) {
        String error = ClientFxManager.startBench(fx, seconds);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal("bhfx bench: " + error));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("bhfx bench: " + seconds / 2 + " s without " + fx
                + ", then " + seconds / 2 + " s with it where you are looking. Keep the camera still."), false);
        return Command.SINGLE_SUCCESS;
    }
}
