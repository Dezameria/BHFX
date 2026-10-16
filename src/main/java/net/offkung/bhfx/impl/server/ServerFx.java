package net.offkung.bhfx.impl.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.offkung.bhfx.BHFxMod;
import net.offkung.bhfx.api.Audience;
import net.offkung.bhfx.api.FxHandle;
import net.offkung.bhfx.api.FxKey;
import net.offkung.bhfx.api.FxParams;
import net.offkung.bhfx.api.FxSpawn;
import net.offkung.bhfx.api.StopMode;
import net.offkung.bhfx.impl.BHFxConfig;
import net.offkung.bhfx.impl.net.BHFxNetwork;
import net.offkung.bhfx.impl.net.S2CFxStart;
import net.offkung.bhfx.impl.net.S2CFxStop;
import net.offkung.bhfx.impl.net.S2CFxUpdate;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side bookkeeping: ids, keys, expiry, rate limits, and re-sending persistent effects to players who arrive
 * while they play (the tracker). Server thread only.
 */
public final class ServerFx {
    /** Expiry used for effects that rely on the client-side default duration. */
    private static final int ASSUMED_DEFAULT_TICKS = 200;
    private static final int GRACE_TICKS = 40;
    /** Players must move this far past the range before they count as having left (avoids resend flapping). */
    private static final double LEAVE_MARGIN = 16.0;

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
        final Set<UUID> sentTo = new HashSet<>();
        FxParams params;
        boolean followSeen;

        Active(int id, ServerLevel level, ResourceLocation fxId, FxSpawn spawn, long seed, @Nullable FxKey key) {
            this.id = id;
            this.dimension = level.dimension();
            this.fxId = fxId;
            this.spawn = spawn;
            this.seed = seed;
            this.startTick = level.getGameTime();
            int d = spawn.duration();
            this.expiresAt = d == FxSpawn.UNTIL_STOPPED ? Long.MAX_VALUE
                    : startTick + (d == FxSpawn.DEFAULT_DURATION ? ASSUMED_DEFAULT_TICKS : d) + GRACE_TICKS;
            this.persistent = spawn.isPersistent();
            this.range = Math.min(spawn.range(), BHFxConfig.SERVER_MAX_RANGE.get());
            this.key = key;
            this.params = spawn.builtParams();
        }

        S2CFxStart packet(int startAge) {
            return new S2CFxStart(id, fxId, spawn.origin(), spawn.target(), spawn.casterId(), spawn.followId(),
                    spawn.follow(), seed, spawn.duration(), startAge, params);
        }
    }

    private record KeyInLevel(ResourceKey<Level> dimension, FxKey key) {
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static final Map<KeyInLevel, Integer> KEYS = new HashMap<>();
    private static final Map<ResourceKey<Level>, long[]> STARTS_THIS_TICK = new HashMap<>();
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

    private static boolean withinLimits(ServerLevel level, FxSpawn spawn, ResourceLocation fxId) {
        long[] counter = STARTS_THIS_TICK.computeIfAbsent(level.dimension(), d -> new long[]{-1, 0});
        long now = level.getGameTime();
        if (counter[0] != now) {
            counter[0] = now;
            counter[1] = 0;
        }
        if (counter[1] >= BHFxConfig.MAX_STARTS_PER_TICK.get()) {
            warnLimit("too many starts this tick in " + level.dimension().location() + "; dropped " + fxId);
            return false;
        }
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
        counter[1]++;
        return true;
    }

    private static void warnLimit(String message) {
        long now = System.currentTimeMillis();
        if (now - lastLimitWarning > 10_000L) {
            lastLimitWarning = now;
            BHFxMod.LOGGER.warn("bhfx rate limit: {} (further warnings muted for 10 s)", message);
        }
    }

    /** Non-persistent effects: one packet to whoever is in range now. */
    private static void broadcast(ServerLevel level, Active a) {
        S2CFxStart packet = a.packet(0);
        Vec3 at = a.spawn.origin();
        Audience audience = a.spawn.audience();
        switch (audience.kind()) {
            case PLAYERS -> {
                for (ServerPlayer player : audience.players()) {
                    BHFxNetwork.send(PacketDistributor.PLAYER.with(() -> player), packet);
                }
            }
            case EXCEPT_CASTER -> {
                Entity caster = a.spawn.casterId() >= 0 ? level.getEntity(a.spawn.casterId()) : null;
                ServerPlayer excluded = caster instanceof ServerPlayer p ? p : null;
                BHFxNetwork.send(PacketDistributor.NEAR.with(() ->
                        new PacketDistributor.TargetPoint(excluded, at.x, at.y, at.z, a.range, level.dimension())), packet);
            }
            default -> BHFxNetwork.send(PacketDistributor.NEAR.with(() ->
                    new PacketDistributor.TargetPoint(at.x, at.y, at.z, a.range, level.dimension())), packet);
        }
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
        if (e != null && !e.isRemoved()) {
            a.followSeen = true;
            return false;
        }
        return a.followSeen;
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
        double enter = a.range * a.range;
        double leave = (a.range + LEAVE_MARGIN) * (a.range + LEAVE_MARGIN);
        S2CFxStart packet = null;
        for (ServerPlayer player : level.players()) {
            if (!audienceAllows(a, player)) {
                continue;
            }
            double d2 = player.position().distanceToSqr(at);
            UUID uuid = player.getUUID();
            if (a.sentTo.contains(uuid)) {
                if (d2 > leave) {
                    a.sentTo.remove(uuid);
                }
            } else if (d2 <= enter) {
                if (packet == null) {
                    packet = a.packet(startAge);
                }
                S2CFxStart p = packet;
                BHFxNetwork.send(PacketDistributor.PLAYER.with(() -> player), p);
                a.sentTo.add(uuid);
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
            a.sentTo.remove(player);
        }
    }

    static void unload(ResourceKey<Level> dimension) {
        ACTIVE.values().removeIf(a -> a.dimension.equals(dimension));
        KEYS.keySet().removeIf(k -> k.dimension().equals(dimension));
        STARTS_THIS_TICK.remove(dimension);
    }

    static void clearAll() {
        ACTIVE.clear();
        KEYS.clear();
        STARTS_THIS_TICK.clear();
    }

    static int activeCount() {
        return ACTIVE.size();
    }
}
