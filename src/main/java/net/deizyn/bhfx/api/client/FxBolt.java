package net.deizyn.bhfx.api.client;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * Draws a lightning bolt or a beam between two world points as ribbons in a {@link FxParticles.Facing#RIBBON} batch:
 * a jagged line made by halving (each midpoint pushed sideways, half as far each round), with optional forks. With
 * {@link #jitter}(0) it is a straight beam.
 * <pre>{@code
 * // constructor
 * bolts = createParticles(FxParticles.RIBBON, 1024).facing(FxParticles.Facing.RIBBON).blend(FxParticles.Blend.ADD);
 * // tick(), every 2-3 ticks while the lightning lasts: a new shape each time makes it flicker
 * FxBolt.between(hand, target).jitter(0.8f).forks(3).width(0.12f).color(0xA0C8FF, 1f).life(0.12f)
 *         .draw(bolts, random());
 * }</pre>
 * The points are spawned at once and live for {@link #life}; they don't move. One bolt uses
 * {@link #points()} slots of the batch's capacity. The object is plain data: keep and reuse it, or make one per bolt.
 */
@ApiStatus.Experimental
public final class FxBolt {
    private static final int MAX_SEGMENTS = 128;

    private Vec3 from;
    private Vec3 to;
    private int segments;
    private float jitter = -1f;
    private int forks;
    private float forkLength = 0.35f;
    private float width = 0.1f;
    private float taper = 0.5f;
    private int rgb = 0xFFFFFF;
    private float alpha = 1f;
    private float life = 0.15f;
    private float delay;

    // scratch for one line: offsets along two sideways axes
    private final float[] offA = new float[MAX_SEGMENTS + 1];
    private final float[] offB = new float[MAX_SEGMENTS + 1];

    private FxBolt(Vec3 from, Vec3 to) {
        this.from = from;
        this.to = to;
    }

    /** A bolt from {@code from} to {@code to} (world points). */
    public static FxBolt between(Vec3 from, Vec3 to) {
        return new FxBolt(from, to);
    }

    /** Move the ends (to reuse the object). */
    public FxBolt ends(Vec3 from, Vec3 to) {
        this.from = from;
        this.to = to;
        return this;
    }

    /** Pieces of the main line, 1..128 (rounded up to a power of two). Default: about three per block, 8..64. */
    public FxBolt segments(int segments) {
        this.segments = segments;
        return this;
    }

    /** How far the middle may stray sideways, in blocks. 0 = a straight beam. Default: 12 % of the length. */
    public FxBolt jitter(float blocks) {
        this.jitter = Math.max(0f, blocks);
        return this;
    }

    /** Side branches leaving the main line (0..8), each thinner and dimmer and bent 15..45 degrees away. */
    public FxBolt forks(int count) {
        this.forks = Math.max(0, Math.min(8, count));
        return this;
    }

    /** Length of a fork as a share of the main line's length (default 0.35). */
    public FxBolt forkLength(float share) {
        this.forkLength = Math.max(0.05f, Math.min(1f, share));
        return this;
    }

    /** Width of the strip in blocks (default 0.1). The built-in ribbon material is brightest in its middle third. */
    public FxBolt width(float blocks) {
        this.width = Math.max(0.001f, blocks);
        return this;
    }

    /** Width at the far end as a share of the width at the start (default 0.5; 1 = even, for beams). */
    public FxBolt taper(float endShare) {
        this.taper = Math.max(0f, endShare);
        return this;
    }

    public FxBolt color(int rgb, float alpha) {
        this.rgb = rgb;
        this.alpha = alpha;
        return this;
    }

    /** How long the drawn shape stays, in seconds (default 0.15). */
    public FxBolt life(float seconds) {
        this.life = Math.max(0.01f, seconds);
        return this;
    }

    /** Wait before it shows, in seconds. */
    public FxBolt delay(float seconds) {
        this.delay = Math.max(0f, seconds);
        return this;
    }

    /** Batch slots one {@link #draw} uses with the current settings: the main line and every fork. */
    public int points() {
        int main = mainSegments();
        return main + 1 + forks * (Math.max(2, main / 4) + 1);
    }

    private int mainSegments() {
        int n = segments > 0 ? segments : (int) Math.max(8, Math.min(64, from.distanceTo(to) * 3.0));
        n = Math.max(1, Math.min(MAX_SEGMENTS, n));
        return Integer.highestOneBit(n) == n ? n : Math.min(MAX_SEGMENTS, Integer.highestOneBit(n) << 1);
    }

    /** Spawn the bolt into {@code batch} (a RIBBON batch), shaped by {@code random} (an effect's {@code random()}). */
    public void draw(FxParticles batch, RandomSource random) {
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (batch == null || random == null || length < 1e-4) {
            return;
        }
        int n = mainSegments();
        float amp = jitter >= 0f ? jitter : (float) (length * 0.12);
        line(batch, random, from, d, n, amp, width, alpha);
        // forks start on the main line as it was drawn; their own lines reuse offA / offB
        System.arraycopy(offA, 0, mainA, 0, n + 1);
        System.arraycopy(offB, 0, mainB, 0, n + 1);
        Vec3 dir = d.scale(1.0 / length);
        Vec3 a = sideways(dir);
        Vec3 b = dir.cross(a);
        for (int f = 0; f < forks; f++) {
            // leave somewhere in the first 80 % of the line
            int at = 1 + random.nextInt(Math.max(1, (int) (n * 0.8)));
            double t = at / (double) n;
            Vec3 start = from.add(d.scale(t)).add(a.scale(mainA[at])).add(b.scale(mainB[at]));
            double bend = Math.toRadians(15.0 + random.nextDouble() * 30.0);
            double turn = random.nextDouble() * Math.PI * 2.0;
            Vec3 out = a.scale(Math.cos(turn)).add(b.scale(Math.sin(turn)));
            Vec3 forkDir = dir.scale(Math.cos(bend)).add(out.scale(Math.sin(bend)));
            double forkLen = length * forkLength * (0.6 + 0.4 * random.nextDouble()) * (1.0 - t * 0.5);
            line(batch, random, start, forkDir.scale(forkLen), Math.max(2, n / 4), (float) (amp * forkLength),
                    width * 0.55f, alpha * 0.6f);
        }
    }

    private final float[] mainA = new float[MAX_SEGMENTS + 1];
    private final float[] mainB = new float[MAX_SEGMENTS + 1];

    /** One jagged ribbon from {@code start} along {@code d}; leaves its sideways offsets in offA / offB. */
    private void line(FxParticles batch, RandomSource random, Vec3 start, Vec3 d, int n, float amp, float lineWidth,
                      float lineAlpha) {
        double length = d.length();
        Vec3 dir = d.scale(1.0 / length);
        Vec3 a = sideways(dir);
        Vec3 b = dir.cross(a);
        offA[0] = offB[0] = 0f;
        offA[n] = offB[n] = 0f;
        float reach = amp;
        for (int step = n; step > 1; step >>= 1) {
            int half = step >> 1;
            for (int i = half; i < n; i += step) {
                offA[i] = (offA[i - half] + offA[i + half]) * 0.5f + (random.nextFloat() * 2f - 1f) * reach;
                offB[i] = (offB[i - half] + offB[i + half]) * 0.5f + (random.nextFloat() * 2f - 1f) * reach;
            }
            reach *= 0.55f;
        }
        FxParticles.Ribbon ribbon = batch.ribbon();
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double x = start.x + d.x * t + a.x * offA[i] + b.x * offB[i];
            double y = start.y + d.y * t + a.y * offA[i] + b.y * offB[i];
            double z = start.z + d.z * t + a.z * offA[i] + b.z * offB[i];
            float w = lineWidth * (float) (1.0 + (taper - 1.0) * t);
            ribbon.spawn(x, y, z).size(Math.max(0.001f, w)).life(life).delay(delay).color(rgb, lineAlpha);
        }
        ribbon.cut();
    }

    /** Any unit vector square to {@code dir}. */
    private static Vec3 sideways(Vec3 dir) {
        Vec3 up = Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return dir.cross(up).normalize();
    }
}
