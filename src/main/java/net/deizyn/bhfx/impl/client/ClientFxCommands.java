package net.deizyn.bhfx.impl.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.deizyn.bhfx.api.StopMode;
import net.deizyn.bhfx.api.client.LocalFxHandle;
import net.deizyn.bhfx.impl.FxCommandArgs;
import net.deizyn.bhfx.impl.FxCommands;
import net.deizyn.bhfx.impl.FxStress;
import net.deizyn.bhfx.api.FxSpawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * /bhfx test &lt;fx&gt; [at &lt;pos&gt; | attach &lt;entities&gt;] [{params}] and
 * /bhfx stop [&lt;entities&gt; | fx &lt;id&gt; | near &lt;radius&gt; | name &lt;name&gt;] [finish]:
 * plays registered effects on this client only (see {@link FxCommands}, {@link FxCommandArgs}).
 */
final class ClientFxCommands {
    private static final FxCommands.Started<LocalFxHandle> STARTED =
            new FxCommands.Started<>(ClientFxManager::isLocalAlive, ClientFxManager::stopLocal);

    private ClientFxCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> test() {
        return Commands.literal("test").then(FxCommands.play(
                (ctx, builder) -> SharedSuggestionProvider.suggestResource(ClientFxManager.registeredIds(), builder),
                false, ClientFxCommands::play));
    }

    static LiteralArgumentBuilder<CommandSourceStack> stop() {
        return FxCommands.stop(STARTED, ClientFxCommands::stop);
    }

    static void clear() {
        STARTED.clear();
        STRESS.clear();
    }

    // ------------------------------------------------------------------ stress

    /** A running /bhfx stress and who asked for it. */
    private record StressRun(FxStress plan, CommandSourceStack source, ResourceLocation level) {
    }

    private static final List<StressRun> STRESS = new ArrayList<>();

    static LiteralArgumentBuilder<CommandSourceStack> stress() {
        return FxCommands.stress(ClientFxManager::registeredIds, ClientFxCommands::stress);
    }

    private static int stress(CommandContext<CommandSourceStack> ctx, int count, double radius, int seconds, String typed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        if (!ClientFxManager.enabled()) {
            ctx.getSource().sendFailure(Component.literal("bhfx stress: bhfx is off"));
            return 0;
        }
        Collection<ResourceLocation> ids;
        try {
            ids = typed != null ? FxStress.parseIds(typed) : ClientFxManager.registeredIds();
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("bhfx stress: not an effect id: " + e.getMessage()));
            return 0;
        }
        Set<ResourceLocation> known = ClientFxManager.registeredIds();
        for (ResourceLocation id : ids) {
            if (!known.contains(id)) {
                ctx.getSource().sendFailure(Component.literal("bhfx stress: " + id + " is not a registered effect"));
                return 0;
            }
        }
        if (ids.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("bhfx stress: no effects registered"));
            return 0;
        }
        FxStress plan = new FxStress(ids, mc.player.position(), radius, count, seconds, mc.level.random.nextLong());
        StressRun run = new StressRun(plan, ctx.getSource(), mc.level.dimension().location());
        ClientFxManager.resetCounters();
        ctx.getSource().sendSuccess(() -> Component.literal("bhfx stress: " + plan.describe()
                + " (only you see them). /bhfx stop ends them; /bhfx stats shows the counters."), false);
        if (!step(run)) { // with no spread everything starts now, like a real burst
            STRESS.add(run);
        }
        return plan.count();
    }

    /** Client tick. */
    static void tick() {
        if (!STRESS.isEmpty()) {
            STRESS.removeIf(ClientFxCommands::step);
        }
    }

    /** Start what is due now; true when the run is finished (and reported). */
    private static boolean step(StressRun run) {
        FxStress plan = run.plan();
        int due = plan.due();
        for (int i = 0; i < due; i++) {
            ResourceLocation fx = plan.id(plan.startedCount() - due + i);
            Vec3 at = plan.nextPoint();
            LocalFxHandle handle = ClientFxManager.startLocal(fx, FxSpawn.at(at).target(at), true);
            if (handle.isNone()) {
                plan.refused();
            } else {
                STARTED.add(handle, fx, null, at, run.level());
            }
        }
        if (!plan.done()) {
            return false;
        }
        int ok = plan.count() - plan.refusedCount();
        run.source().sendSuccess(() -> Component.literal("bhfx stress: started " + ok + " of " + plan.count()
                + (plan.refusedCount() > 0 ? " (" + plan.refusedCount() + " could not start)" : "")
                + ", " + ClientFxManager.counterLine()), false);
        return true;
    }

    private static int play(CommandContext<CommandSourceStack> ctx, FxCommands.Shape shape) throws CommandSyntaxException {
        ResourceLocation fx = ResourceLocationArgument.getId(ctx, "fx");
        if (!ClientFxManager.registeredIds().contains(fx)) {
            ctx.getSource().sendFailure(Component.literal("bhfx: " + fx + " is not a registered effect"));
            return 0;
        }
        FxCommandArgs args = FxCommands.args(ctx, shape);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return 0;
        }
        if (shape.attach()) {
            List<Entity> targets = ClientSelector.select(FxCommands.raw(ctx, "who"), mc.level, player, player.position());
            if (targets.size() > FxCommands.MAX_TARGETS) {
                ctx.getSource().sendFailure(Component.literal("bhfx: " + targets.size() + " entities matched; at most "
                        + FxCommands.MAX_TARGETS + " (add limit=...)"));
                return 0;
            }
            int started = 0;
            for (Entity target : targets) {
                LocalFxHandle handle = ClientFxManager.startLocal(fx, FxCommands.spawnOn(target, args));
                if (!handle.isNone()) {
                    STARTED.add(handle, fx, args.name(), target);
                    started++;
                }
            }
            if (started == 0) {
                return off(ctx, fx);
            }
            int n = started;
            ctx.getSource().sendSuccess(() -> Component.literal("bhfx test: " + fx + " on "
                    + (n == 1 ? targets.get(0).getName().getString() : n + " entities") + " (only you see it)"), false);
            return n;
        }
        Vec3 at = shape.at() ? Vec3Argument.getVec3(ctx, "pos") : lookTarget(mc, player);
        LocalFxHandle handle = ClientFxManager.startLocal(fx, FxCommands.spawnAt(at, player, args));
        if (handle.isNone()) {
            return off(ctx, fx);
        }
        STARTED.add(handle, fx, args.name(), at, mc.level.dimension().location());
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("bhfx test: %s at %d %d %d (only you see it)",
                fx, (int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z))), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx, FxCommands.StopBy by, StopMode mode)
            throws CommandSyntaxException {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        List<Entity> on = by == FxCommands.StopBy.ENTITIES
                ? ClientSelector.select(FxCommands.raw(ctx, "who"), mc.level, mc.player, mc.player.position()) : null;
        int n = STARTED.stop(FxCommands.which(ctx, by, on, mc.player.position(), mc.level.dimension().location()), mode);
        ctx.getSource().sendSuccess(() -> Component.literal("bhfx: stopped " + n + " test effect(s)"
                + FxCommands.describe(ctx, by) + (mode == StopMode.FINISH ? " (full ending)" : "")), false);
        return Math.max(1, n);
    }

    private static int off(CommandContext<CommandSourceStack> ctx, ResourceLocation fx) {
        ctx.getSource().sendFailure(Component.literal("bhfx: could not start " + fx + " (bhfx is off)"));
        return 0;
    }

    private static Vec3 lookTarget(Minecraft mc, LocalPlayer player) {
        HitResult hit = player.pick(64.0, 1f, false);
        return hit.getType() == HitResult.Type.MISS
                ? player.getEyePosition().add(player.getLookAngle().scale(24.0)) : hit.getLocation();
    }
}
