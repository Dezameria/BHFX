package net.deizyn.bhfx.api.client;

import net.deizyn.bhfx.impl.render.LevelGround;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.function.LongSupplier;

/**
 * Where the ground is, on this client's blocks: the same lookup {@link FxEmitter.Shape#onGround} uses (the top of a
 * block's collision shape or a fluid's surface; leaves count, plants without collision don't). Not a heightmap: the
 * client only has some of Minecraft's heightmaps, and the server-side ones read wrong here.
 * <ul>
 *   <li>{@link #surface}: one point, now.</li>
 *   <li>{@link #batch}: many points (a strike point per sword), looked up a few at a time within a time budget, so a
 *       thousand lookups don't land in one frame.</li>
 * </ul>
 * Render thread only (the client's ticks and frames).
 */
@ApiStatus.Experimental
public final class FxGround {
    private FxGround() {
    }

    /**
     * The surface nearest the point: if it is inside a block or fluid, the top of that column (searching up); else the
     * top of the first block or fluid below. NaN when there is none within {@code search} blocks, or no world.
     */
    public static double surface(double x, double y, double z, double search) {
        return LevelGround.surface(x, y, z, search);
    }

    /** A batch of up to {@code capacity} points, each looked up within {@code search} blocks of its own height. */
    public static Batch batch(int capacity, double search) {
        return new Batch(capacity, search, FxGround::surface, System::nanoTime);
    }

    /**
     * Points looked up in the order they were added, a few per {@link #update} call. Results come in order, so
     * {@link #resolvedCount()} is also "points 0 .. n - 1 are done".
     */
    public static final class Batch {
        private final double search;
        private final FxEmitter.GroundProbe probe;
        private final LongSupplier clock;
        private final double[] xs;
        private final double[] ys;
        private final double[] zs;
        private final double[] surface;
        private int added;
        private int resolved;

        Batch(int capacity, double search, FxEmitter.GroundProbe probe, LongSupplier clock) {
            int n = Math.max(0, capacity);
            this.search = search;
            this.probe = probe;
            this.clock = clock;
            this.xs = new double[n];
            this.ys = new double[n];
            this.zs = new double[n];
            this.surface = new double[n];
            Arrays.fill(surface, Double.NaN);
        }

        /** Queue a point; returns its index, or -1 when the batch is full. */
        public int add(double x, double y, double z) {
            if (added >= xs.length) {
                return -1;
            }
            xs[added] = x;
            ys[added] = y;
            zs[added] = z;
            return added++;
        }

        /**
         * Look up queued points until about {@code maxMillis} has been spent (at least one per call while any are
         * left). Call it from {@code tick} or {@code passes}. Returns how many were resolved by this call.
         */
        public int update(double maxMillis) {
            if (resolved >= added) {
                return 0;
            }
            long start = clock.getAsLong();
            long budget = (long) (Math.max(0.0, maxMillis) * 1.0e6);
            int done = 0;
            do {
                surface[resolved] = probe.surface(xs[resolved], ys[resolved], zs[resolved], search);
                resolved++;
                done++;
            } while (resolved < added && clock.getAsLong() - start < budget);
            return done;
        }

        /** Resolve everything left now (for when the answers are needed this instant). */
        public void finish() {
            while (resolved < added) {
                surface[resolved] = probe.surface(xs[resolved], ys[resolved], zs[resolved], search);
                resolved++;
            }
        }

        public int size() {
            return added;
        }

        public int resolvedCount() {
            return resolved;
        }

        public boolean done() {
            return resolved >= added;
        }

        public boolean resolved(int index) {
            return index >= 0 && index < resolved;
        }

        /** The surface's height at point {@code index}: NaN while not looked up yet, or when there was none. */
        public double surface(int index) {
            return resolved(index) ? surface[index] : Double.NaN;
        }

        /** {@link #surface(int)}, or {@code fallback} while unknown or when there is none. */
        public double surfaceOr(int index, double fallback) {
            double s = surface(index);
            return Double.isNaN(s) ? fallback : s;
        }
    }
}
