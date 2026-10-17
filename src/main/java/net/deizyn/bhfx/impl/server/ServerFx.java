package net.deizyn.bhfx.impl.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.Audience;
import net.deizyn.bhfx.api.FxHandle;
import net.deizyn.bhfx.api.FxKey;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.FxSpawn;
import net.deizyn.bhfx.api.ScreenPulse;
import net.deizyn.bhfx.api.StopMode;
import net.deizyn.bhfx.impl.BHFxConfig;
import net.deizyn.bhfx.impl.net.BHFxNetwork;
import net.deizyn.bhfx.impl.net.S2CFxStart;
import net.deizyn.bhfx.impl.net.S2CFxStartBatch;
import net.deizyn.bhfx.impl.net.S2CFxStop;
import net.deizyn.bhfx.impl.net.S2CFxUpdate;
import net.deizyn.bhfx.impl.net.S2CScreenPulse;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side bookkeeping: ids, keys, expiry, rate limits, and re-sending persistent effects to players who arrive
 * while they play (the tracker). Starts are queued per player and sent at the end of the server tick, several in one
 * packet; a stop or update first flushes the queue so the client always sees a start before its stop. Server thread
 * only.
 */
public final class ServerFx {
    private static final class Active {
        final int id;
        final ResourceKey<Level> dimension;
        final ResourceLocation fxId;
        final FxSpawn spawn;
        final long seed;
        final long startTick;
        final long expiresAt;
        final boolean persistent;
        final double range;
        @Nullable
        final FxKey key;
        final Tracking.Reach reach;
        final Tracking.Follow follow;
        FxParams params;

        Active(int id, ServerLevel level, ResourceLocation fxId, FxSpawn spawn, long seed, @Nullable FxKey key) {
            this.id = id;
            this.dimension = level.dimension();
            this.fxId = fxId;
            this.spawn = spawn;
            this.seed = seed;
            this.startTick = level.getGameTime();
            this.expiresAt = Tracking.expiresAt(startTick, spawn.duration());
            this.persistent = spawn.isPersistent();
            this.range = Math.min(spawn.range(), BHFxConfig.SERVER_MAX_RANGE.get());
            this.key = key;
            this.params = spawn.builtParams();
            this.reach = new Tracking.Reach(range);
            // seen now, so losing it before the first tracker pass still ends the effect (otherwise it could never expire)
            this.follow = new Tracking.Follow(spawn.followId() >= 0,
                    spawn.followId() >= 0 && level.getEntity(spawn.followId()) != null);
        }

        S2CFxStart packet(int startAge) {
            return new S2CFxStart(id, fxId, spawn.origin(), spawn.target(), spawn.casterId(), spawn.followId(),
                    spawn.follow(), seed, spawn.duration(), startAge, params);
        }
    }

    private record KeyInLevel(ResourceKey<Level> dimension, FxKey key) {
    }

    private static final class Outbox {
        final ServerPlayer player;
        final List<S2CFxStart> starts = new ArrayList<>(4);

        Outbox(ServerPlayer player) {
            this.player = player;
        }
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static final Map<KeyInLevel, Integer> KEYS = new HashMap<>();
    private static final Map<ResourceKey<Level>, long[]> STARTS_THIS_TICK = new HashMap<>();
    private static final Map<UUID, Outbox> OUTBOX = new LinkedHashMap<>();
    private static int nextId = 1;
    private static long lastLimitWarning;

    private ServerFx() {
    }

    public static boolean enabled() {
        return BHFxConfig.SERVER_ENABLED.get();
    }

    // ------------------------------------------------------------------ start / stop / update

    public static FxHandle start(ServerLevel level, @Nullable FxKey key, ResourceLocation fxId, FxSpawn spawn) {
        if (!enabled()) {
            return FxHandle.NONE;
        }
        if (key != null) {
            stop(level, key, StopMode.IMMEDIATE);
        }
        if (!withinLimits(level, spawn, fxId)) {
            return FxHandle.NONE;
        }
        int id = nextId++;
        if (nextId <= 0) {
            nextId = 1;
        }
        long seed = spawn.seed() != null ? spawn.seed() : level.random.nextLong();
        Active a = new Active(id, level, fxId, spawn, seed, key);
        ACTIVE.put(id, a);
        if (key != null) {
            KEYS.put(new KeyInLevel(level.dimension(), key), id);
        }
        if (a.persistent) {
            sendToNewPlayers(level, a, 0);
        } else {
            broadcast(level, a);
        }
        return new FxHandle(level.dimension(), id);
    }

    // ------------------------------------------------------------------ pulses

    public static void pulse(ServerLevel level, Vec3 at, double range, ScreenPulse pulse) {
        if (!enabled() || pulse == null || pulse.isEmpty() || !takeStart(level, "a screen pulse")) {
            return;
        }
        double r = Math.min(range, BHFxConfig.SERVER_MAX_RANGE.get());
        S2CScreenPulse packet = new S2CScreenPulse(at, (float) r, pulse);
        double r2 = r * r;
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) <= r2) {
                BHFxNetwork.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    public static void pulse(ServerPlayer player, ScreenPulse pulse) {
        if (!enabled() || pulse == null || pulse.isEmpty() || !takeStart(player.serverLevel(), "a screen pulse")) {
            return;
        }
        BHFxNetwork.send(PacketDistributor.PLAYER.with(() -> player), new S2CScreenPulse(null, 0f, pulse));
    }

    /** Count one start against this tick's per-dimension limit; false (and a muted warning) when it is used up. */
    private static boolean takeStart(ServerLevel level, String what) {
        long[] counter = STARTS_THIS_TICK.computeIfAbsent(level.dimension(), d -> new long[]{-1, 0});
        long now = level.getGameTime();
        if (counter[0] != now) {
            counter[0] = now;
            counter[1] = 0;
        }
        if (counter[1] >= BHFxConfig.MAX_STARTS_PER_TICK.get()) {
            warnLimit("too many starts this tick in " + level.dimension().location() + "; dropped " + what);
            return false;
        }
        counter[1]++;
        return true;
    }

    private static boolean withinLimits(ServerLevel level, FxSpawn spawn, ResourceLocation fxId) {
        int caster = spawn.casterId();
        if (caster >= 0) {
            int owned = 0;
            for (Active a : ACTIVE.values()) {
                if (a.spawn.casterId() == caster && a.dimension.equals(level.dimension())) {
                    owned++;
                }
            }
            if (owned >= BHFxConfig.MAX_ACTIVE_PER_CASTER.get()) {
                warnLimit("caster " + caster + " already owns " + owned + " effects; dropped " + fxId);
                return false;
            }
        }
        return takeStart(level, fxId.toString());
    }

    private static void warnLimit(String message) {
        long now = System.currentTimeMillis();
        if (now - lastLimitWarning > 10_000L) {
            lastLimitWarning = now;
            BHFxMod.LOGGER.warn("bhfx rate limit: {} (further warnings muted for 10 s)", message);
        }
    }

    /** Non-persistent effects: one start to whoever is in range now. */
    private static void broadcast(ServerLevel level, Active a) {
        S2CFxStart packet = a.packet(0);
        Audience audience = a.spawn.audience();
        if (audience.kind() == Audience.Kind.PLAYERS) {
            for (ServerPlayer player : audience.players()) {
                queue(player, packet);
            }
            return;
        }
        Vec3 at = a.spawn.origin();
        double r2 = a.range * a.range;
        for (ServerPlayer player : level.players()) {
            if (audienceAllows(a, player) && player.position().distanceToSqr(at) <= r2) {
                queue(player, packet);
            }
        }
    }

    private static void queue(ServerPlayer player, S2CFxStart packet) {
        OUTBOX.computeIfAbsent(player.getUUID(), u -> new Outbox(player)).starts.add(packet);
    }

    /** Send every queued start. Called at the end of each server tick and before any stop or update. */
    static void flush() {
        if (OUTBOX.isEmpty()) {
            return;
        }
        for (Outbox box : OUTBOX.values()) {
            if (box.player.hasDisconnected()) {
                continue;
            }
            PacketDistributor.PacketTarget target = PacketDistributor.PLAYER.with(() -> box.player);
            List<S2CFxStart> starts = box.starts;
            if (starts.size() == 1) {
                BHFxNetwork.send(target, starts.get(0));
                continue;
            }
            for (int i = 0; i < starts.size(); i += S2CFxStartBatch.MAX) {
                List<S2CFxStart> part = starts.subList(i, Math.min(starts.size(), i + S2CFxStartBatch.MAX));
                BHFxNetwork.send(target, part.size() == 1 ? part.get(0) : new S2CFxStartBatch(new ArrayList<>(part)));
            }
        }
        OUTBOX.clear();
    }

    public static void stop(FxHandle handle, StopMode mode) {
        if (handle == null || handle.isNone()) {
            return;
        }
        Active a = ACTIVE.remove(handle.id());
        if (a == null) {
            return;
        }
        if (a.key != null) {
            KEYS.remove(new KeyInLevel(a.dimension, a.key));
        }
        // Stop packets are tiny and unknown ids are ignored client-side, so send to the whole dimension:
        // the effect may follow an entity far from where it started.
        flush();
        BHFxNetwork.send(PacketDistributor.DIMENSION.with(handle::dimension), new S2CFxStop(handle.id(), mode));
    }

    public static void stop(ServerLevel level, FxKey key, StopMode mode) {
        Integer id = KEYS.get(new KeyInLevel(level.dimension(), key));
        if (id != null) {
            stop(new FxHandle(level.dimension(), id), mode);
        }
    }

    public static Optional<FxHandle> handle(ServerLevel level, FxKey key) {
        Integer id = KEYS.get(new KeyInLevel(level.dimension(), key));
        return id != null && ACTIVE.containsKey(id) ? Optional.of(new FxHandle(level.dimension(), id)) : Optional.empty();
    }

    public static void update(FxHandle handle, FxParams changed) {
        if (handle == null || handle.isNone() || changed.isEmpty()) {
            return;
        }
        Active a = ACTIVE.get(handle.id());
        if (a == null) {
            return;
        }
        a.params = a.params.merge(changed);
        S2CFxUpdate packet = new S2CFxUpdate(a.id, changed);
        flush();
        BHFxNetwork.send(PacketDistributor.DIMENSION.with(handle::dimension), packet);
    }

    public static boolean isAlive(FxHandle handle) {
        return handle != null && !handle.isNone() && ACTIVE.containsKey(handle.id());
    }

    // ------------------------------------------------------------------ tracker

    /** Every 10 ticks per level: expire finished effects and send persistent ones to players who just arrived. */
    static void track(ServerLevel level) {
        long now = level.getGameTime();
        ResourceKey<Level> dim = level.dimension();
        Iterator<Active> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (!a.dimension.equals(dim)) {
                continue;
            }
            if (now > a.expiresAt || followLost(level, a)) {
                it.remove();
                if (a.key != null) {
                    KEYS.remove(new KeyInLevel(a.dimension, a.key));
                }
                continue;
            }
            if (a.persistent) {
                sendToNewPlayers(level, a, (int) Math.min(Integer.MAX_VALUE, now - a.startTick));
            }
        }
    }

    private static boolean followLost(ServerLevel level, Active a) {
        if (a.spawn.followId() < 0) {
            return false;
        }
        Entity e = level.getEntity(a.spawn.followId());
        return a.follow.lost(e != null && !e.isRemoved());
    }

    private static Vec3 position(ServerLevel level, Active a) {
        if (a.spawn.followId() >= 0) {
            Entity e = level.getEntity(a.spawn.followId());
            if (e != null) {
                return e.position();
            }
        }
        return a.spawn.origin();
    }

    private static void sendToNewPlayers(ServerLevel level, Active a, int startAge) {
        Vec3 at = position(level, a);
        S2CFxStart packet = null;
        for (ServerPlayer player : level.players()) {
            if (audienceAllows(a, player) && a.reach.shouldSend(player.getUUID(), player.position().distanceToSqr(at))) {
                if (packet == null) {
                    packet = a.packet(startAge);
                }
                queue(player, packet);
            }
        }
    }

    private static boolean audienceAllows(Active a, ServerPlayer player) {
        Audience audience = a.spawn.audience();
        return switch (audience.kind()) {
            case TRACKING -> true;
            case EXCEPT_CASTER -> player.getId() != a.spawn.casterId();
            case PLAYERS -> {
                for (ServerPlayer p : audience.players()) {
                    if (p.getUUID().equals(player.getUUID())) {
                        yield true;
                    }
                }
                yield false;
            }
        };
    }

    /** The player's client lost its effects (logout, respawn, dimension change): send them again when in range. */
    static void forget(UUID player) {
        for (Active a : ACTIVE.values()) {
            a.reach.forget(player);
        }
        OUTBOX.remove(player);
    }

    static void unload(ResourceKey<Level> dimension) {
        ACTIVE.values().removeIf(a -> a.dimension.equals(dimension));
        KEYS.keySet().removeIf(k -> k.dimension().equals(dimension));
        STARTS_THIS_TICK.remove(dimension);
    }

    static void clearAll() {
        OUTBOX.clear();
        ACTIVE.clear();
        KEYS.clear();
        STARTS_THIS_TICK.clear();
    }

    static int activeCount() {
        return ACTIVE.size();
    }
}
