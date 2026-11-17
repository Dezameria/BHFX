package net.deizyn.bhfx.api;

import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.ApiStatus;

/**
 * Random numbers that depend only on an effect's seed, a stream and an index, not on how many numbers were drawn
 * before. Use it for anything every client (and the server) must agree on whenever they compute it: strike points and
 * times, the spawns of one tick, the shape of bolt number {@code n}.
 * <p>
 * {@code Fx.random()} is one sequence: an effect built late (a player who joins or walks into range mid-effect)
 * has drawn fewer numbers, so from then on its numbers differ from everyone else's. A value keyed here is the same
 * for everyone:
 * <pre>{@code
 * // server, when it starts the effect with FxSpawn.seed(seed), and the Fx on every client with init().seed():
 * long STRIKES = FxRandom.stream("strikes");
 * double angle = FxRandom.unit(seed, STRIKES, i) * Math.PI * 2;      // strike i, one value
 * RandomSource r = FxRandom.source(seed, STRIKES, i);                // strike i, as many values as needed
 * }</pre>
 * In an {@code Fx}, {@code random(stream, index)} is the same as {@code source(init().seed(), stream, index)}.
 * A stream is any number that names one use ({@link #stream(String)} makes one from a name); the index numbers the
 * draws inside it (a tick, an item's index). Pure and thread-safe; works on both sides.
 */
@ApiStatus.Experimental
public final class FxRandom {
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private FxRandom() {
    }

    /** A stream number from a name, the same in every JVM (it does not use {@link String#hashCode} of the runtime's locale or identity). */
    public static long stream(String name) {
        long h = 0xCBF29CE484222325L; // FNV-1a, 64 bit
        for (int i = 0; i < name.length(); i++) {
            h ^= name.charAt(i);
            h *= 0x100000001B3L;
        }
        return h;
    }

    /** The mixed seed of {@code (seed, stream, index)}: well spread even when the three differ by one. */
    public static long seed(long seed, long stream, long index) {
        long h = mix(seed + GOLDEN);
        h = mix(h ^ (stream + GOLDEN) * 0xBF58476D1CE4E5B9L);
        return mix(h ^ (index + GOLDEN) * 0x94D049BB133111EBL);
    }

    /** A new random source for {@code (seed, stream, index)}; draw as many values from it as that item needs. */
    public static RandomSource source(long seed, long stream, long index) {
        return RandomSource.create(seed(seed, stream, index));
    }

    /** One value in 0 (inclusive) .. 1 (exclusive) for {@code (seed, stream, index)}, without making a source. */
    public static float unit(long seed, long stream, long index) {
        return (seed(seed, stream, index) >>> 40) * 0x1.0p-24f;
    }

    /** One value in {@code from} .. {@code to} for {@code (seed, stream, index)}. */
    public static float range(long seed, long stream, long index, float from, float to) {
        return from + (to - from) * unit(seed, stream, index);
    }

    private static long mix(long z) { // the SplitMix64 finalizer
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
