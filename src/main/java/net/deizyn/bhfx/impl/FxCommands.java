package net.deizyn.bhfx.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The shared grammar of /bhfx test (client, only you see it) and /bhfx_play fx (server, everyone in range):
 * <pre>
 * &lt;fx&gt; [at &lt;pos&gt; | attach &lt;entities&gt;] [to &lt;players&gt;] [{params}]     (to: /bhfx_play only)
 * stop [&lt;entities&gt; | fx &lt;id&gt; | near &lt;radius&gt; | name &lt;name&gt;] [finish]
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

    /** Which effects a stop picks: all, those attached to {@code who}, one effect id, those near the runner, or a name. */
    public enum StopBy { ALL, ENTITIES, FX, NEAR, NAME }

    @FunctionalInterface
    public interface Stop {
        int run(CommandContext<CommandSourceStack> ctx, StopBy by, StopMode mode) throws CommandSyntaxException;
    }

    /** Largest radius of stop near, in blocks. */
    public static final double MAX_NEAR = 512.0;

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

    /**
     * stop [&lt;entities&gt; | fx &lt;id&gt; | near &lt;radius&gt; | name &lt;name&gt;] [finish]. Without a scope it stops everything
     * the command started. Without finish the effects fade out (CANCEL).
     */
    public static LiteralArgumentBuilder<CommandSourceStack> stop(Started<?> started, Stop stop) {
        LiteralArgumentBuilder<CommandSourceStack> root = withFinish(Commands.literal("stop"), StopBy.ALL, stop);
        root.then(withFinish(Commands.argument("who", EntityArgument.entities()), StopBy.ENTITIES, stop));
        root.then(Commands.literal("fx").then(withFinish(Commands.argument("fx", ResourceLocationArgument.id())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(started.fxIds(), b)), StopBy.FX, stop)));
        root.then(Commands.literal("near").then(withFinish(
                Commands.argument("radius", DoubleArgumentType.doubleArg(0.0, MAX_NEAR)), StopBy.NEAR, stop)));
        root.then(Commands.literal("name").then(withFinish(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(started.names(), b)), StopBy.NAME, stop)));
        return root;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withFinish(T node, StopBy by, Stop stop) {
        return node.executes(ctx -> stop.run(ctx, by, StopMode.CANCEL))
                .then(Commands.literal("finish").executes(ctx -> stop.run(ctx, by, StopMode.FINISH)));
    }

    /**
     * What a stop of kind {@code by} matches. {@code on} is the resolved {@code who} (the client and server resolve
     * selectors differently); {@code from} and {@code dimension} (its id, e.g. minecraft:overworld) are where the runner
     * stands, for near.
     */
    public static Predicate<Started.Info> which(CommandContext<CommandSourceStack> ctx, StopBy by,
                                                @Nullable Collection<? extends Entity> on, Vec3 from,
                                                ResourceLocation dimension) {
        return switch (by) {
            case ALL -> e -> true;
            case ENTITIES -> {
                Set<UUID> ids = new HashSet<>();
                if (on != null) {
                    on.forEach(entity -> ids.add(entity.getUUID()));
                }
                yield e -> e.attached() != null && ids.contains(e.attached());
            }
            case FX -> {
                ResourceLocation fx = ResourceLocationArgument.getId(ctx, "fx");
                yield e -> e.fx().equals(fx);
            }
            case NEAR -> {
                double r = DoubleArgumentType.getDouble(ctx, "radius");
                yield e -> e.dimension().equals(dimension) && e.position().distanceToSqr(from) <= r * r;
            }
            case NAME -> {
                String name = StringArgumentType.getString(ctx, "name");
                yield e -> name.equals(e.name());
            }
        };
    }

    /** " within 10 blocks", " named a", ... for the stop message. */
    public static String describe(CommandContext<CommandSourceStack> ctx, StopBy by) {
        return switch (by) {
            case ALL -> "";
            case ENTITIES -> " on " + raw(ctx, "who");
            case FX -> " of " + ResourceLocationArgument.getId(ctx, "fx");
            case NEAR -> " within " + trim(DoubleArgumentType.getDouble(ctx, "radius")) + " blocks";
            case NAME -> " named " + StringArgumentType.getString(ctx, "name");
        };
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    @FunctionalInterface
    public interface Stress {
        /** {@code ids} is the typed text after the numbers, or null for "every registered effect". */
        int run(CommandContext<CommandSourceStack> ctx, int count, double radius, int seconds, @Nullable String ids)
                throws CommandSyntaxException;
    }

    /** Default radius of stress, in blocks. */
    public static final double STRESS_RADIUS = 32.0;

    /**
     * stress &lt;count&gt; [&lt;radius&gt; [&lt;seconds&gt; [&lt;fx&gt; ...]]]: {@code count} effects round the runner, all at
     * once (seconds 0) or spread over the seconds, taking the listed ids (or every registered one) in turn.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> stress(@Nullable Supplier<Collection<ResourceLocation>> known,
                                                                   Stress stress) {
        var fx = Commands.argument("fx", StringArgumentType.greedyString())
                .executes(ctx -> stress.run(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                        DoubleArgumentType.getDouble(ctx, "radius"), IntegerArgumentType.getInteger(ctx, "seconds"),
                        StringArgumentType.getString(ctx, "fx")));
        if (known != null) {
            fx.suggests((ctx, builder) -> {
                String remaining = builder.getRemaining();
                int last = Math.max(remaining.lastIndexOf(' '), remaining.lastIndexOf(',')) + 1;
                return SharedSuggestionProvider.suggestResource(known.get(), builder.createOffset(builder.getStart() + last));
            });
        }
        return Commands.literal("stress").then(Commands.argument("count", IntegerArgumentType.integer(1, FxStress.MAX_COUNT))
                .executes(ctx -> stress.run(ctx, IntegerArgumentType.getInteger(ctx, "count"), STRESS_RADIUS, 0, null))
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.0, FxStress.MAX_RADIUS))
                        .executes(ctx -> stress.run(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                                DoubleArgumentType.getDouble(ctx, "radius"), 0, null))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, FxStress.MAX_SECONDS))
                                .executes(ctx -> stress.run(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                                        DoubleArgumentType.getDouble(ctx, "radius"),
                                        IntegerArgumentType.getInteger(ctx, "seconds"), null))
                                .then(fx))));
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

    /**
     * Effects started by a command, so stop can find them. Starting the same effect on the same entity replaces it;
     * several effects may share a name (stop name ends them together).
     */
    public static final class Started<H> {
        /** What a stop can pick an effect by. {@code position} is the attached entity's current one, else the start point. */
        public record Info(ResourceLocation fx, @Nullable UUID attached, @Nullable String name,
                           ResourceLocation dimension, Vec3 position) {
        }

        private static final class Entry<H> {
            final H handle;
            final ResourceLocation fx;
            final @Nullable String name;
            final @Nullable UUID attached;
            // weak: a removed entity must not be kept alive by a finished command's record
            final @Nullable WeakReference<Entity> entity;
            final Vec3 at;
            final ResourceLocation dimension;

            Entry(H handle, ResourceLocation fx, @Nullable String name, @Nullable Entity attached, Vec3 at,
                  ResourceLocation dimension) {
                this.handle = handle;
                this.fx = fx;
                this.name = name;
                this.attached = attached != null ? attached.getUUID() : null;
                this.entity = attached != null ? new WeakReference<>(attached) : null;
                this.at = at;
                this.dimension = dimension;
            }

            Info info() {
                Entity e = entity != null ? entity.get() : null;
                if (e != null && !e.isRemoved()) {
                    return new Info(fx, attached, name, e.level().dimension().location(), e.position());
                }
                return new Info(fx, attached, name, dimension, at);
            }
        }

        private final List<Entry<H>> entries = new ArrayList<>();
        private final Predicate<H> alive;
        private final BiConsumer<H, StopMode> stopper;

        public Started(Predicate<H> alive, BiConsumer<H, StopMode> stopper) {
            this.alive = alive;
            this.stopper = stopper;
        }

        /** An effect on {@code attached}: its position and dimension follow the entity. */
        public void add(H handle, ResourceLocation fx, @Nullable String name, Entity attached) {
            add(handle, fx, name, attached, attached.position(), attached.level().dimension().location());
        }

        /** An effect fixed at {@code at} in the dimension with id {@code dimension}. */
        public void add(H handle, ResourceLocation fx, @Nullable String name, Vec3 at, ResourceLocation dimension) {
            add(handle, fx, name, null, at, dimension);
        }

        private void add(H handle, ResourceLocation fx, @Nullable String name, @Nullable Entity attached, Vec3 at,
                         ResourceLocation dimension) {
            prune();
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
            entries.add(new Entry<>(handle, fx, name, attached, at, dimension));
        }

        /** Stops the playing effects {@code which} matches. Returns how many were stopped. */
        public int stop(Predicate<Info> which, StopMode mode) {
            prune();
            int stopped = 0;
            var it = entries.iterator();
            while (it.hasNext()) {
                Entry<H> e = it.next();
                if (which.test(e.info())) {
                    stopper.accept(e.handle, mode);
                    stopped++;
                    it.remove();
                }
            }
            return stopped;
        }

        /** Ids of the effects still playing, for suggestions. */
        public Set<ResourceLocation> fxIds() {
            prune();
            Set<ResourceLocation> ids = new TreeSet<>();
            entries.forEach(e -> ids.add(e.fx));
            return ids;
        }

        /** Names of the effects still playing, for suggestions. */
        public Set<String> names() {
            prune();
            Set<String> names = new TreeSet<>();
            entries.forEach(e -> {
                if (e.name != null) {
                    names.add(e.name);
                }
            });
            return names;
        }

        public void clear() {
            entries.clear();
        }

        private void prune() {
            entries.removeIf(e -> !alive.test(e.handle));
        }
    }
}
