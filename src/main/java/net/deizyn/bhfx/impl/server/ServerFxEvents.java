package net.deizyn.bhfx.impl.server;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.BHFx;
import net.deizyn.bhfx.api.FxParam;
import net.deizyn.bhfx.api.FxSpawn;
import net.deizyn.bhfx.api.ScreenPulse;

public final class ServerFxEvents {
    private ServerFxEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level && level.getGameTime() % 10 == 0) {
            ServerFx.track(level);
        }
    }

    /** Send the starts queued this tick (after every level ticked, so the tracker's sends go out in the same tick). */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerFx.flush();
        }
    }

    // a client loses its effects on logout, respawn and dimension change; let the tracker send them again

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerFx.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ServerFx.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ServerFx.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ServerFx.unload(level.dimension());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerFx.clearAll();
    }

    /**
     * /bhfx_test [radius] — plays the built-in test effect where the player looks.
     * /bhfx_test pulse — a screen pulse at the player (shake, flash, red edges, colour fringes). Permission level 2.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bhfx_test")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> test(ctx, 8f))
                .then(Commands.literal("pulse").executes(ServerFxEvents::testPulse))
                .then(Commands.argument("radius", FloatArgumentType.floatArg(2f, 64f))
                        .executes(ctx -> test(ctx, FloatArgumentType.getFloat(ctx, "radius")))));
    }

    private static int testPulse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!BHFx.available(player.serverLevel())) {
            ctx.getSource().sendFailure(Component.literal("bhfx is disabled in bhfx-server.toml"));
            return 0;
        }
        BHFx.pulse(player.serverLevel(), player.position(), 32, ScreenPulse.of()
                .shake(0.5f)
                .flash(0xFFF0D8, 0.7f, 8)
                .vignette(0x500808, 0.6f, 24)
                .aberration(0.8f, 12));
        ctx.getSource().sendSuccess(() -> Component.literal("bhfx pulse (players within 32 blocks)"), false);
        return 1;
    }

    private static int test(CommandContext<CommandSourceStack> ctx, float radius) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (!BHFx.available(level)) {
            ctx.getSource().sendFailure(Component.literal("bhfx is disabled in bhfx-server.toml"));
            return 0;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(160.0));
        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? eye.add(player.getLookAngle().scale(32.0)) : hit.getLocation();
        BHFx.start(level, BHFxMod.id("test"), FxSpawn.at(player.position()).target(target).caster(player)
                .param(FxParam.RADIUS, radius).duration(160));
        ctx.getSource().sendSuccess(() -> Component.literal("bhfx test at " + (int) target.x + " " + (int) target.y + " " + (int) target.z), false);
        return 1;
    }
}
