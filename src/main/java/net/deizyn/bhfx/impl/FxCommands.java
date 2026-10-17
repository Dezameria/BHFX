package net.deizyn.bhfx.impl;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.deizyn.bhfx.api.Follow;
import net.deizyn.bhfx.api.FxSpawn;
import net.deizyn.bhfx.api.StopMode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * The shared grammar of /bhfx test (client, only you see it) and /bhfx_play fx (server, everyone in range):
 * <pre>
 * &lt;fx&gt; [at &lt;pos&gt; | attach &lt;entities&gt;] [to &lt;players&gt;] [{params}]     (to: /bhfx_play only)
 * stop [&lt;entities&gt;] [finish]
 * </pre>
 * Each side only supplies how to resolve entities and how to start and stop an effect.
 */
public final class FxCommands {
    /** Most effects one command may start (one per attached entity). */
    public static final int MAX_TARGETS = 32;

    private FxCommands() {
    }

    /** Which optional parts the typed command used. */
    public record Shape(boolean at, boolean attach, boolean to, boolean params) {
        Shape withTo() { return new Shape(at, attach, true, params); }
        Shape withParams() { return new Shape(at, attach, to, true); }
    }

    @FunctionalInterface
    public interface Play {
        int run(CommandContext<CommandSourceStack> ctx, Shape shape) throws CommandSyntaxException;
    }

    @FunctionalInterface
    public interface Stop {
        int run(CommandContext<CommandSourceStack> ctx, boolean who, StopMode mode) throws CommandSyntaxException;
    }

    public static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> play(
            @Nullable SuggestionProvider<CommandSourceStack> fxSuggestions, boolean audience, Play play) {
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> fx = Commands.argument("fx", ResourceLocationArgument.id());
        if (fxSuggestions != null) {
            fx.suggests(fxSuggestions);
        }
        tail(fx, new Shape(false, false, false, false), audience, play);
        fx.then(Commands.literal("at").then(tail(Commands.argument("pos", Vec3Argument.vec3()),
                new Shape(true, false, false, false), audience, play)));
        fx.then(Commands.literal("attach").then(tail(Commands.argument("who", EntityArgument.entities()),
                new Shape(false, true, false, false), audience, play)));
        return fx;
    }

    /** stop, stop finish, stop &lt;entities&gt;, stop &lt;entities&gt; finish. Without finish the effects fade out (CANCEL). */
    public static LiteralArgumentBuilder<CommandSourceStack> stop(Stop stop) {
        return Commands.literal("stop")
                .executes(ctx -> stop.run(ctx, false, StopMode.CANCEL))
                .then(Commands.literal("finish").executes(ctx -> stop.run(ctx, false, StopMode.FINISH)))
                .then(Commands.argument("who", EntityArgument.entities())
                        .executes(ctx -> stop.run(ctx, true, StopMode.CANCEL))
                        .then(Commands.literal("finish").executes(ctx -> stop.run(ctx, true, StopMode.FINISH))));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T tail(T node, Shape shape, boolean audience, Play play) {
        node.executes(ctx -> play.run(ctx, shape));
        node.then(Commands.argument("params", CompoundTagArgument.compoundTag())
                .executes(ctx -> play.run(ctx, shape.withParams())));
        if (audience && !shape.to()) {
            node.then(Commands.literal("to").then(tail(Commands.argument("viewers", EntityArgument.players()),
                    shape.withTo(), false, play)));
        }
        return node;
    }

    public static FxCommandArgs args(CommandContext<CommandSourceStack> ctx, Shape shape) throws CommandSyntaxException {
        return FxCommandArgs.from(shape.params() ? CompoundTagArgument.getCompoundTag(ctx, "params") : null);
    }

    /** The text typed for an argument, e.g. the raw selector of {@code who} (the client evaluates it itself). */
    public static String raw(CommandContext<CommandSourceStack> ctx, String name) {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (node.getNode().getName().equals(name)) {
                return node.getRange().get(ctx.getInput());
            }
        }
        throw new IllegalArgumentException("No argument " + name);
    }

    /** An effect fixed at a point; the command's runner is the caster when there is one. */
    public static FxSpawn spawnAt(Vec3 at, @Nullable Entity caster, FxCommandArgs args) {
        FxSpawn spawn = FxSpawn.at(at).target(at);
        if (caster != null) {
            spawn.caster(caster);
        }
        return args.apply(spawn);
    }

    /** An effect riding on an entity, which is also its caster (anchors such as "the caster's eyes" use it). */
    public static FxSpawn spawnOn(Entity entity, FxCommandArgs args) {
        Vec3 at = entity.position();
        return args.apply(FxSpawn.at(at).target(at).caster(entity).follow(entity, Follow.POSITION_AND_LOOK));
    }

    /** Effects started by a command, so stop can find them. Starting the same effect on the same entity replaces it. */
    public static final class Started<H> {
        private record Entry<H>(H handle, ResourceLocation fx, @Nullable UUID attached) {
        }

        private final List<Entry<H>> entries = new ArrayList<>();
        private final Predicate<H> alive;
        private final BiConsumer<H, StopMode> stopper;

        public Started(Predicate<H> alive, BiConsumer<H, StopMode> stopper) {
            this.alive = alive;
            this.stopper = stopper;
        }

        public void add(H handle, ResourceLocation fx, @Nullable Entity attached) {
            entries.removeIf(e -> !alive.test(e.handle));
            if (attached != null) {
                UUID id = attached.getUUID();
                entries.removeIf(e -> {
                    boolean same = e.fx.equals(fx) && id.equals(e.attached);
                    if (same) {
                        stopper.accept(e.handle, StopMode.CANCEL);
                    }
                    return same;
                });
            }
            entries.add(new Entry<>(handle, fx, attached != null ? attached.getUUID() : null));
        }

        /** Stops everything, or only the effects attached to {@code on}. Returns how many were still playing. */
        public int stop(@Nullable Collection<? extends Entity> on, StopMode mode) {
            List<UUID> ids = on == null ? null : on.stream().map(Entity::getUUID).toList();
            int stopped = 0;
            var it = entries.iterator();
            while (it.hasNext()) {
                Entry<H> e = it.next();
                if (ids != null && (e.attached == null || !ids.contains(e.attached))) {
                    continue;
                }
                if (alive.test(e.handle)) {
                    stopper.accept(e.handle, mode);
                    stopped++;
                }
                it.remove();
            }
            return stopped;
        }

        public void clear() {
            entries.clear();
        }
    }
}
