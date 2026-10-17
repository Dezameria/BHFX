package net.deizyn.bhfx.impl.server;

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
import net.deizyn.bhfx.api.BHFx;
import net.deizyn.bhfx.api.FxHandle;
import net.deizyn.bhfx.api.StopMode;
import net.deizyn.bhfx.impl.FxCommandArgs;
import net.deizyn.bhfx.impl.FxCommands;
import net.deizyn.bhfx.api.Audience;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.deizyn.bhfx.impl.client.ClientFxManager;
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
        PLAYED.clear();
    }

    /** Effects started by /bhfx_play, so /bhfx_play stop can end them. */
    private static final FxCommands.Started<FxHandle> PLAYED = new FxCommands.Started<>(BHFx::isAlive, BHFx::stop);

    /**
     * /bhfx_play fx &lt;fx&gt; [at &lt;pos&gt; | attach &lt;entities&gt;] [to &lt;players&gt;] [{params}] — like the client's /bhfx test,
     * but sent to every player in range, or only to {@code to} (full vanilla selectors, including tag=).
     * /bhfx_play stop [&lt;entities&gt;] [finish] — fades out (or plays the full ending of) what this command started.
     * /bhfx_play pulse — a screen pulse at the player. Permission level 2. Effects are registered on clients, so a
     * dedicated server can neither check nor suggest ids (try them with /bhfx test first); single player and LAN hosts
     * suggest them, because the client is in the same game.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bhfx_play")
                .requires(source -> source.hasPermission(2))
                .then(FxCommands.stop(ServerFxEvents::stopPlayed))
                .then(Commands.literal("pulse").executes(ServerFxEvents::pulse))
                .then(Commands.literal("fx").then(FxCommands.play(ServerFxEvents::suggestFx, true, ServerFxEvents::play))));
    }

    private static CompletableFuture<Suggestions> suggestFx(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        // only an integrated server shares the game with a client; a dedicated server never loads the client class
        if (FMLEnvironment.dist.isClient()) {
            return SharedSuggestionProvider.suggestResource(ClientFxManager.registeredIds(), builder);
        }
        return builder.buildFuture();
    }

    private static int play(CommandContext<CommandSourceStack> ctx, FxCommands.Shape shape) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!BHFx.available(source.getLevel())) {
            source.sendFailure(Component.literal("bhfx is disabled in bhfx-server.toml"));
            return 0;
        }
        ResourceLocation fx = ResourceLocationArgument.getId(ctx, "fx");
        FxCommandArgs args = FxCommands.args(ctx, shape);
        Collection<ServerPlayer> viewers = shape.to() ? EntityArgument.getPlayers(ctx, "viewers") : null;
        String who = viewers == null ? "players in range" : viewers.size() == 1
                ? viewers.iterator().next().getName().getString() : viewers.size() + " players";

        if (shape.attach()) {
            Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "who");
            if (targets.size() > FxCommands.MAX_TARGETS) {
                source.sendFailure(Component.literal("bhfx: " + targets.size() + " entities matched; at most "
                        + FxCommands.MAX_TARGETS + " (add limit=...)"));
                return 0;
            }
            int started = 0;
            for (Entity target : targets) {
                if (target.level() instanceof ServerLevel level
                        && start(level, fx, audience(FxCommands.spawnOn(target, args), viewers), target)) {
                    started++;
                }
            }
            if (started == 0) {
                return limited(source, fx);
            }
            int n = started;
            Entity first = targets.iterator().next();
            source.sendSuccess(() -> Component.literal("bhfx play: " + fx + " on "
                    + (n == 1 ? first.getName().getString() : n + " entities") + ", seen by " + who), true);
            return n;
        }

        Entity runner = source.getEntity();
        Vec3 at = shape.at() ? Vec3Argument.getVec3(ctx, "pos")
                : runner != null ? lookTarget(runner, source.getLevel()) : source.getPosition();
        if (!start(source.getLevel(), fx, audience(FxCommands.spawnAt(at, runner, args), viewers), null)) {
            return limited(source, fx);
        }
        source.sendSuccess(() -> Component.literal(String.format("bhfx play: %s at %d %d %d, seen by %s",
                fx, (int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z), who)), true);
        return 1;
    }

    private static boolean start(ServerLevel level, ResourceLocation fx, FxSpawn spawn, @Nullable Entity attached) {
        FxHandle handle = BHFx.start(level, fx, spawn);
        if (handle.isNone()) {
            return false;
        }
        PLAYED.add(handle, fx, attached);
        return true;
    }

    private static FxSpawn audience(FxSpawn spawn, @Nullable Collection<ServerPlayer> viewers) {
        return viewers == null ? spawn : spawn.audience(Audience.players(viewers));
    }

    private static int limited(CommandSourceStack source, ResourceLocation fx) {
        source.sendFailure(Component.literal("bhfx: " + fx + " was not started (rate limit)"));
        return 0;
    }

    private static int stopPlayed(CommandContext<CommandSourceStack> ctx, boolean who, StopMode mode) throws CommandSyntaxException {
        Collection<? extends Entity> on = who ? EntityArgument.getEntities(ctx, "who") : null;
        int n = PLAYED.stop(on, mode);
        ctx.getSource().sendSuccess(() -> Component.literal("bhfx: stopped " + n + " effect(s) started by /bhfx_play"
                + (mode == StopMode.FINISH ? " (full ending)" : "")), true);
        return Math.max(1, n);
    }

    private static Vec3 lookTarget(Entity entity, ServerLevel level) {
        Vec3 eye = entity.getEyePosition();
        Vec3 end = eye.add(entity.getLookAngle().scale(160.0));
        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return hit.getType() == HitResult.Type.MISS ? eye.add(entity.getLookAngle().scale(32.0)) : hit.getLocation();
    }

    private static int pulse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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
}
