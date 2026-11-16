package net.deizyn.bhfx.impl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.PriorityQueue;

/**
 * Sounds and shakes that travel: they reach this client's camera {@code distance / speed} seconds after they set out,
 * the way thunder comes after the flash. Sounds go through Minecraft's own delayed sound queue; shakes wait here and
 * fire on the client tick they arrive. Render thread only. Cleared with the world.
 */
public final class Waves {
    /** Speed of sound in air: a block is a metre. */
    public static final float SPEED_OF_SOUND = 343f;
    /** Most shakes waiting at once; past it the newest are dropped (each still adds to the same trauma). */
    static final int MAX_PENDING = 256;

    private static final Schedule<Shake> SHAKES = new Schedule<>(MAX_PENDING);
    private static final RandomSource RANDOM = RandomSource.create();
    private static long now;

    private Waves() {
    }

    private record Shake(Vec3 source, float amount, double range) {
    }

    /** Seconds a wave from {@code at} takes to reach {@code listener}; 0 for a speed of 0 or less (instant). */
    public static double delaySeconds(Vec3 listener, Vec3 at, float speed) {
        if (!(speed > 0f)) {
            return 0.0;
        }
        return listener.distanceTo(at) / speed;
    }

    /** {@link #delaySeconds} in client ticks, rounded. */
    public static int delayTicks(double seconds) {
        return (int) Math.max(0L, Math.round(seconds * 20.0));
    }

    /** Seconds a wave from {@code at} takes to reach this client's camera. */
    public static double delayToCamera(Vec3 at, float speed) {
        return delaySeconds(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), at, speed);
    }

    /** Play {@code sound} at {@code at} once its wave reaches the camera. */
    public static void sound(SoundEvent sound, Vec3 at, float volume, float pitch, float speed) {
        if (sound == null || at == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        SimpleSoundInstance instance = new SimpleSoundInstance(sound, SoundSource.PLAYERS, volume, pitch, RANDOM,
                at.x, at.y, at.z);
        int ticks = delayTicks(delayToCamera(at, speed));
        if (ticks <= 0) {
            mc.getSoundManager().play(instance);
        } else {
            mc.getSoundManager().playDelayed(instance, ticks);
        }
    }

    /** Shake the camera (like {@code ctx.shake}) once the wave from {@code source} reaches it. */
    public static void shake(Vec3 source, float amount, double range, float speed) {
        if (source == null || !(amount > 0f)) {
            return;
        }
        int ticks = delayTicks(delayToCamera(source, speed));
        if (ticks <= 0) {
            ClientFxManager.shake(source, amount, range);
        } else {
            SHAKES.add(now + ticks, new Shake(source, amount, range));
        }
    }

    /** Fire what has arrived. Once per client tick. */
    static void tick() {
        now++;
        Shake s;
        while ((s = SHAKES.poll(now)) != null) {
            ClientFxManager.shake(s.source(), s.amount(), s.range());
        }
    }

    static void clear() {
        SHAKES.clear();
    }

    /** Things due at a tick, soonest first, bounded. Pure, so it is tested on its own. */
    static final class Schedule<T> {
        private record Item<T>(long due, long order, T value) {
        }

        private final PriorityQueue<Item<T>> queue = new PriorityQueue<>((a, b) -> a.due != b.due
                ? Long.compare(a.due, b.due) : Long.compare(a.order, b.order));
        private final int max;
        private long order;

        Schedule(int max) {
            this.max = max;
        }

        /** False (and nothing kept) when full. */
        boolean add(long due, T value) {
            if (queue.size() >= max) {
                return false;
            }
            queue.add(new Item<>(due, order++, value));
            return true;
        }

        /** The next value due at or before {@code now}, or null. */
        T poll(long now) {
            Item<T> head = queue.peek();
            if (head == null || head.due > now) {
                return null;
            }
            queue.poll();
            return head.value;
        }

        int size() {
            return queue.size();
        }

        void clear() {
            queue.clear();
        }
    }
}
