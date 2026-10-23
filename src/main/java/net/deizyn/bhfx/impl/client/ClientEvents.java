package net.deizyn.bhfx.impl.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.impl.BHFxConfig;
import net.deizyn.bhfx.impl.block.MirrorTracker;

/**
 * Forge-bus hooks. bhfx needs no mixins: it renders at {@code AFTER_LEVEL}, after the world and before the hand, and
 * draws mirror reflections at {@code AFTER_SOLID_BLOCKS} (offscreen) and {@code AFTER_BLOCK_ENTITIES} (onto the mirrors).
 */
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientFxManager.tick(Minecraft.getInstance());
            MirrorRenderer.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY || stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            // these stages carry the camera-rotation pose stack (AFTER_LEVEL carries the projection stack instead)
            ClientFxManager.captureMatrices(event.getPoseStack().last().pose(), event.getProjectionMatrix());
            if (stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
                MirrorRenderer.drawReflectionsInLevel(event.getPoseStack(), event.getCamera(), event.getFrustum(),
                        event.getPartialTick());
            }
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            MirrorRenderer.composite(event.getPoseStack(), event.getCamera());
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

    /** Last, so the angles are final: with a shader pack the mirror reflections are drawn here (see MirrorRenderer). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAnglesFinal(ViewportEvent.ComputeCameraAngles event) {
        MirrorRenderer.drawReflectionsBeforeLevel(event);
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
        ClientFxCommands.clear();
        MirrorTracker.clear();
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
                .then(ClientFxCommands.test())
                .then(ClientFxCommands.stop())
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
        ctx.getSource().sendSuccess(() -> Component.literal("bhfx bench: playing " + fx + " where you are looking for "
                + seconds + " s, drawing switched off/on every second. Keep the camera still."), false);
        return Command.SINGLE_SUCCESS;
    }
}
