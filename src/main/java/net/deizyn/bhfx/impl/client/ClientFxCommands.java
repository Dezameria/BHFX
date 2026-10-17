package net.deizyn.bhfx.impl.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.deizyn.bhfx.api.StopMode;
import net.deizyn.bhfx.api.client.LocalFxHandle;
import net.deizyn.bhfx.impl.FxCommandArgs;
import net.deizyn.bhfx.impl.FxCommands;
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

import java.util.List;

/**
 * /bhfx test &lt;fx&gt; [at &lt;pos&gt; | attach &lt;entities&gt;] [{params}] and /bhfx stop [&lt;entities&gt;] [finish]:
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
        return FxCommands.stop(ClientFxCommands::stop);
    }

    static void clear() {
        STARTED.clear();
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
                    STARTED.add(handle, fx, target);
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
        STARTED.add(handle, fx, null);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("bhfx test: %s at %d %d %d (only you see it)",
                fx, (int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z))), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx, boolean who, StopMode mode) throws CommandSyntaxException {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        List<Entity> on = who ? ClientSelector.select(FxCommands.raw(ctx, "who"), mc.level, mc.player, mc.player.position()) : null;
        int n = STARTED.stop(on, mode);
        ctx.getSource().sendSuccess(() -> Component.literal("bhfx: stopped " + n + " test effect(s)"
                + (mode == StopMode.FINISH ? " (full ending)" : "")), false);
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
