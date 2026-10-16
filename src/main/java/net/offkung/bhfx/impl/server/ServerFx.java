package net.offkung.bhfx.impl.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.offkung.bhfx.api.FxHandle;
import net.offkung.bhfx.api.FxSpawn;
import net.offkung.bhfx.api.StopMode;
import net.offkung.bhfx.impl.BHFxConfig;
import net.offkung.bhfx.impl.net.BHFxNetwork;
import net.offkung.bhfx.impl.net.S2CFxStart;
import net.offkung.bhfx.impl.net.S2CFxStop;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Server-side bookkeeping for started effects (ids, expiry). Server thread only. */
public final class ServerFx {
    /** Expiry used for effects that rely on the client-side default duration. */
    private static final int ASSUMED_DEFAULT_TICKS = 200;
    private static final int GRACE_TICKS = 40;

    private record Active(ResourceKey<Level> dimension, long expiresAt) {
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static int nextId = 1;

    private ServerFx() {
    }

    public static boolean enabled() {
        return BHFxConfig.SERVER_ENABLED.get();
    }

    public static FxHandle start(ServerLevel level, ResourceLocation fxId, FxSpawn spawn) {
        if (!enabled()) {
            return FxHandle.NONE;
        }
        int id = nextId++;
        if (nextId <= 0) {
            nextId = 1;
        }
        long seed = spawn.seed() != null ? spawn.seed() : level.random.nextLong();
        S2CFxStart packet = new S2CFxStart(id, fxId, spawn.origin(), spawn.target(), spawn.casterId(), spawn.followId(),
                spawn.follow(), seed, spawn.duration(), 0, spawn.builtParams());
        send(level, spawn, packet);

        int duration = spawn.duration();
        long expires = duration == FxSpawn.UNTIL_STOPPED ? Long.MAX_VALUE
                : level.getGameTime() + (duration == FxSpawn.DEFAULT_DURATION ? ASSUMED_DEFAULT_TICKS : duration) + GRACE_TICKS;
        ACTIVE.put(id, new Active(level.dimension(), expires));
        return new FxHandle(level.dimension(), id);
    }

    private static void send(ServerLevel level, FxSpawn spawn, S2CFxStart packet) {
        double range = Math.min(spawn.range(), BHFxConfig.SERVER_MAX_RANGE.get());
        Vec3 at = spawn.origin();
        switch (spawn.audience().kind()) {
            case PLAYERS -> {
                for (ServerPlayer player : spawn.audience().players()) {
                    BHFxNetwork.send(PacketDistributor.PLAYER.with(() -> player), packet);
                }
            }
            case EXCEPT_CASTER -> {
                Entity caster = spawn.casterId() >= 0 ? level.getEntity(spawn.casterId()) : null;
                ServerPlayer excluded = caster instanceof ServerPlayer p ? p : null;
                BHFxNetwork.send(PacketDistributor.NEAR.with(() ->
                        new PacketDistributor.TargetPoint(excluded, at.x, at.y, at.z, range, level.dimension())), packet);
            }
            default -> BHFxNetwork.send(PacketDistributor.NEAR.with(() ->
                    new PacketDistributor.TargetPoint(at.x, at.y, at.z, range, level.dimension())), packet);
        }
    }

    public static void stop(FxHandle handle, StopMode mode) {
        if (handle == null || handle.isNone() || ACTIVE.remove(handle.id()) == null) {
            return;
        }
        // Stop packets are tiny and unknown ids are ignored client-side, so send to the whole dimension:
        // the effect may follow an entity far from where it started.
        BHFxNetwork.send(PacketDistributor.DIMENSION.with(handle::dimension), new S2CFxStop(handle.id(), mode));
    }

    public static boolean isAlive(FxHandle handle) {
        return handle != null && !handle.isNone() && ACTIVE.containsKey(handle.id());
    }

    static void prune(ServerLevel level) {
        long now = level.getGameTime();
        ResourceKey<Level> dim = level.dimension();
        Iterator<Active> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (a.dimension().equals(dim) && now > a.expiresAt()) {
                it.remove();
            }
        }
    }

    static void unload(ResourceKey<Level> dimension) {
        ACTIVE.values().removeIf(a -> a.dimension().equals(dimension));
    }

    static void clearAll() {
        ACTIVE.clear();
    }
}
