package net.deizyn.bhfx.impl.render;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxEmitter;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.FxTexture;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * CPU side of an {@link FxParticles} batch: a ring of {@code capacity} particles, {@link #FLOATS} floats each, written
 * by spawns and sent to the GPU only for the slots written since the last upload. Motion is analytic (see the particle
 * vertex shader), so nothing changes between spawns. Render thread only.
 */
public final class ParticleBatch implements FxParticles {
    /**
     * pos.xyz birth | vel.xyz life | size0 size1 rot spin | rgba | data | axis.xyz length | orbit spinUp pull link.
     * RIBBON points keep their ribbon id in axis.x and the previous point's slot + 1 in link (0 = first point).
     */
    static final int FLOATS = 28;
    public static final int MAX_CAPACITY = 16384;

    /** Added to the owner's age to get "now": 1 while ticking (the next frame shows age + 1 + partial), partial in frames. */
    private static float clockOffset = 1f;

    private final Fx owner;
    private final ResourceLocation material;
    private final int capacity;
    final float[] data;
    private final Cursor cursor = new Cursor();

    Blend blend = Blend.ADD;
    Facing facing = Facing.CAMERA;
    float stretch = 0.05f;
    float pivot;
    float gx;
    float gy;
    float gz;
    float drag;
    float softness = 0.4f;
    float distortion = 0.08f;
    /** Orbit axis: through the world point (when set; else the origin), along the unit axis. */
    private boolean orbitSet;
    private double cx;
    private double cy;
    private double cz;
    float oax;
    float oay = 1f;
    float oaz;
    /** Some particle spawned since the last clear orbits or is pulled (the cull sphere must allow for turning). */
    private boolean orbiting;
    private final EmitPointImpl point = new EmitPointImpl();
    private final double[] sample = new double[6];
    /** Where {@link FxEmitter.Shape#onGround} looks for the ground (the client world; replaced in tests). */
    static FxEmitter.GroundProbe ground = LevelGround::surface;
    private int nextRibbon = 1;

    // turbulence: amplitude (blocks), frequency (Hz), pattern scale (waves per block)
    float turbAmp;
    float turbFreq = 1f;
    float turbScale = 0.35f;
    // over-life curves, 0 keys = off
    final float[] colorKeys = new float[12];
    int colorKeyCount;
    @Nullable
    FxTexture ramp;
    float rampFrom;
    float rampTo = 1f;
    final float[] alphaKeys = new float[4];
    int alphaKeyCount;
    final float[] sizeKeys = new float[4];
    int sizeKeyCount;

    private boolean hasOrigin;
    double ox;
    double oy;
    double oz;
    private int head;
    /** Slots in use, 0..capacity (the GPU draws this many instances). */
    int filled;
    /** Slots written since the last upload, capped at capacity; {@link #needsFull} forces all of them. */
    int pending;
    boolean needsFull = true;
    /** Largest reach of any particle from the origin without gravity, and the longest life, for the cull sphere. */
    private float reach;
    /** Largest spawn size, so a size curve above 1 can be added to the cull sphere. */
    private float maxSize;
    private float maxLife;
    /** When the last particle of the batch dies (batch clock). */
    private float lastDeath = Float.NEGATIVE_INFINITY;
    private boolean statsDirty;

    // GPU side, owned by ParticleRenderer
    int gpuBuffer;
    int gpuTexture;
    long lastDrawNanos;
    boolean closed;

    public ParticleBatch(Fx owner, ResourceLocation material, int capacity) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.material = Objects.requireNonNull(material, "material");
        this.capacity = Math.max(1, Math.min(MAX_CAPACITY, capacity));
        this.data = new float[this.capacity * FLOATS];
    }

    /** Set by the effect manager before ticking (1) and before collecting a frame (the partial tick). */
    public static void clockOffset(float offset) {
        clockOffset = offset;
    }

    /** The batch clock in seconds: the owner's age plus the current offset. */
    public float now() {
        return (owner.age() + clockOffset) / 20f;
    }

    /** The owner ended; GPU memory is freed on the next frame. */
    public void close() {
        closed = true;
    }

    @Override
    public ResourceLocation material() {
        return material;
    }

    @Override
    public FxParticles blend(Blend blend) {
        this.blend = Objects.requireNonNull(blend);
        return this;
    }

    @Override
    public FxParticles facing(Facing facing) {
        this.facing = Objects.requireNonNull(facing);
        return this;
    }

    @Override
    public FxParticles stretch(float secondsOfTravel) {
        this.stretch = Math.max(0f, secondsOfTravel);
        return this;
    }

    @Override
    public FxParticles pivot(float pivot) {
        this.pivot = Math.max(0f, Math.min(1f, pivot));
        return this;
    }

    @Override
    public FxParticles gravity(double x, double y, double z) {
        gx = (float) x;
        gy = (float) y;
        gz = (float) z;
        return this;
    }

    @Override
    public FxParticles drag(float perSecond) {
        this.drag = Math.max(0f, perSecond);
        return this;
    }

    @Override
    public FxParticles softness(float blocks) {
        this.softness = Math.max(0f, blocks);
        return this;
    }

    @Override
    public FxParticles distortion(float strength) {
        this.distortion = strength;
        return this;
    }

    @Override
    public FxParticles turbulence(float blocks, float frequency) {
        return turbulence(blocks, frequency, turbScale);
    }

    @Override
    public FxParticles turbulence(float blocks, float frequency, float scale) {
        turbAmp = Math.max(0f, blocks);
        turbFreq = Math.max(0f, frequency);
        turbScale = Math.max(0f, scale);
        return this;
    }

    @Override
    public FxParticles colorOverLife(int... colours) {
        int n = colours == null ? 0 : Math.min(4, colours.length);
        for (int i = 0; i < n; i++) {
            int c = colours[i];
            colorKeys[i * 3] = ((c >> 16) & 0xFF) / 255f;
            colorKeys[i * 3 + 1] = ((c >> 8) & 0xFF) / 255f;
            colorKeys[i * 3 + 2] = (c & 0xFF) / 255f;
        }
        colorKeyCount = n;
        ramp = null;
        return this;
    }

    @Override
    public FxParticles colorOverLife(@Nullable FxTexture ramp, float from, float to) {
        this.ramp = ramp;
        rampFrom = from;
        rampTo = to;
        if (ramp != null) {
            colorKeyCount = 0;
        }
        return this;
    }

    @Override
    public FxParticles alphaOverLife(float... keys) {
        alphaKeyCount = copyKeys(keys, alphaKeys);
        return this;
    }

    @Override
    public FxParticles sizeOverLife(float... keys) {
        sizeKeyCount = copyKeys(keys, sizeKeys);
        return this;
    }

    private static int copyKeys(float[] keys, float[] into) {
        int n = keys == null ? 0 : Math.min(4, keys.length);
        System.arraycopy(keys == null ? into : keys, 0, into, 0, n);
        return n;
    }

    /** Largest size multiplier of the size curve (1 when off). */
    float sizeCurveMax() {
        float m = sizeKeyCount == 0 ? 1f : 0f;
        for (int i = 0; i < sizeKeyCount; i++) {
            m = Math.max(m, sizeKeys[i]);
        }
        return m;
    }

    @Override
    public FxParticles orbit(Vec3 centre, Vec3 axis) {
        return orbit(centre.x, centre.y, centre.z, axis.x, axis.y, axis.z);
    }

    @Override
    public FxParticles orbit(double x, double y, double z, double axisX, double axisY, double axisZ) {
        double l = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        orbitSet = true;
        cx = x;
        cy = y;
        cz = z;
        if (l > 1e-9) {
            oax = (float) (axisX / l);
            oay = (float) (axisY / l);
            oaz = (float) (axisZ / l);
        }
        return this;
    }

    /** The orbit centre relative to the batch origin. */
    float orbitX() {
        return orbitSet ? (float) (cx - ox) : 0f;
    }

    float orbitY() {
        return orbitSet ? (float) (cy - oy) : 0f;
    }

    float orbitZ() {
        return orbitSet ? (float) (cz - oz) : 0f;
    }

    @Override
    public ParticleSpawn spawn(Vec3 world) {
        return spawn(world.x, world.y, world.z);
    }

    @Override
    public ParticleSpawn spawn(double x, double y, double z) {
        if (!hasOrigin) {
            hasOrigin = true;
            ox = x;
            oy = y;
            oz = z;
        }
        int slot = head;
        head = (head + 1) % capacity;
        filled = Math.min(capacity, filled + 1);
        pending = Math.min(capacity, pending + 1);
        statsDirty = true;
        int b = slot * FLOATS;
        float[] d = data;
        d[b] = (float) (x - ox);
        d[b + 1] = (float) (y - oy);
        d[b + 2] = (float) (z - oz);
        d[b + 3] = now();
        d[b + 4] = d[b + 5] = d[b + 6] = 0f;
        d[b + 7] = 1f;
        d[b + 8] = d[b + 9] = 0.25f;
        d[b + 10] = d[b + 11] = 0f;
        d[b + 12] = d[b + 13] = d[b + 14] = d[b + 15] = 1f;
        d[b + 16] = d[b + 17] = d[b + 18] = d[b + 19] = 0f;
        d[b + 20] = 0f;
        d[b + 21] = 1f;
        d[b + 22] = 0f;
        d[b + 23] = 0f;
        d[b + 24] = d[b + 25] = d[b + 26] = d[b + 27] = 0f;
        cursor.base = b;
        return cursor;
    }

    @Override
    public int emit(FxEmitter.Shape shape, Vec3 centre, int count, double speed, @Nullable FxEmitter.EmitAction each) {
        return emit(shape, centre.x, centre.y, centre.z, count, speed, each);
    }

    @Override
    public int emit(FxEmitter.Shape shape, double x, double y, double z, int count, double speed,
                    @Nullable FxEmitter.EmitAction each) {
        Objects.requireNonNull(shape, "shape");
        for (int i = 0; i < count; i++) {
            emitOne(shape, x, y, z, i, count, speed, Float.NaN, null, each);
        }
        return Math.max(0, count);
    }

    /** One particle of an emit; {@code birth} (batch clock) replaces "now" unless NaN. */
    private void emitOne(FxEmitter.Shape shape, double x, double y, double z, int index, int count, double speed,
                         float birth, @Nullable RibbonImpl link, @Nullable FxEmitter.EmitAction each) {
        double[] o = sample;
        shape.sample(owner.bhfxRandom(), index, count, o);
        double px = x + o[0];
        double py = y + o[1];
        double pz = z + o[2];
        if (shape.groundSearch() > 0) {
            double surface = ground.surface(px, py, pz, shape.groundSearch());
            if (!Double.isNaN(surface)) {
                py = surface + shape.groundLift();
            }
        }
        ParticleSpawn spawn = link != null ? link.spawn(px, py, pz) : spawn(px, py, pz);
        spawn.velocity(o[3] * speed, o[4] * speed, o[5] * speed);
        if (!Float.isNaN(birth)) {
            data[cursor.base + 3] = birth;
        }
        if (each != null) {
            EmitPointImpl p = point;
            p.index = index;
            p.count = count;
            p.x = px;
            p.y = py;
            p.z = pz;
            p.dx = o[3];
            p.dy = o[4];
            p.dz = o[5];
            each.accept(spawn, p);
        }
    }

    @Override
    public Stream stream(float perSecond) {
        return new StreamImpl(perSecond);
    }

    @Override
    public Ribbon ribbon() {
        return new RibbonImpl();
    }

    @Override
    public int alive() {
        float now = now();
        int n = 0;
        for (int i = 0; i < filled; i++) {
            int b = i * FLOATS;
            float t = now - data[b + 3];
            if (t >= 0f && t <= data[b + 7]) {
                n++;
            }
        }
        return n;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public void clear() {
        head = filled = pending = 0;
        reach = maxLife = maxSize = 0f;
        orbiting = false;
        lastDeath = Float.NEGATIVE_INFINITY;
        statsDirty = false;
    }

    /** First slot not yet uploaded (the pending slots run from here, wrapping). */
    int pendingStart() {
        return Math.floorMod(head - pending, capacity);
    }

    int capacityValue() {
        return capacity;
    }

    /** Bring the cull sphere and last death up to date with the slots spawned since the last call. */
    void updateStats() {
        if (!statsDirty) {
            return;
        }
        statsDirty = false;
        int start = pendingStart();
        for (int i = 0; i < pending; i++) {
            int b = ((start + i) % capacity) * FLOATS;
            float[] d = data;
            float life = d[b + 7];
            float dist = (float) Math.sqrt(d[b] * d[b] + d[b + 1] * d[b + 1] + d[b + 2] * d[b + 2]);
            float speed = (float) Math.sqrt(d[b + 4] * d[b + 4] + d[b + 5] * d[b + 5] + d[b + 6] * d[b + 6]);
            if (d[b + 24] != 0f || d[b + 25] != 0f || d[b + 26] != 0f) {
                orbiting = true;
                if (d[b + 26] < 0f) {
                    speed -= d[b + 26]; // pushed away from the axis
                }
            }
            float size = Math.max(d[b + 8], d[b + 9]) * (facing == Facing.VELOCITY ? 1f + stretch * speed : 1f);
            if (facing == Facing.AXIS) {
                size = Math.max(size, d[b + 23] > 0f ? d[b + 23] : size * 2f) + Math.max(d[b + 8], d[b + 9]);
            } else {
                size *= 1f + pivot;
            }
            reach = Math.max(reach, dist + speed * life + size);
            maxSize = Math.max(maxSize, Math.max(d[b + 8], d[b + 9]));
            maxLife = Math.max(maxLife, life);
            lastDeath = Math.max(lastDeath, d[b + 3] + life);
        }
    }

    /** Radius of a sphere around the origin that holds every particle, gravity included. */
    float cullRadius() {
        float g = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        float r = reach + 0.5f * g * maxLife * maxLife + 0.5f;
        r += maxSize * Math.max(0f, sizeCurveMax() - 1f) + 2f * turbAmp;
        if (orbiting) {
            // an orbiting particle is within reach of the centre as it is now (its reach is measured from the centre
            // and turning round an axis through it keeps that distance), so within |c| + r of the origin
            float c = (float) Math.sqrt(orbitX() * orbitX() + orbitY() * orbitY() + orbitZ() * orbitZ());
            r += c;
        }
        return r;
    }

    /** Nothing left to draw at {@code now}. */
    public boolean idleAt(float now) {
        updateStats();
        return filled == 0 || now > lastDeath;
    }

    public boolean closed() {
        return closed;
    }

    private final class Cursor implements ParticleSpawn {
        int base;

        @Override
        public ParticleSpawn velocity(double x, double y, double z) {
            data[base + 4] = (float) x;
            data[base + 5] = (float) y;
            data[base + 6] = (float) z;
            return this;
        }

        @Override
        public ParticleSpawn velocity(Vec3 v) {
            return velocity(v.x, v.y, v.z);
        }

        @Override
        public ParticleSpawn life(float seconds) {
            data[base + 7] = Math.max(0.001f, seconds);
            return this;
        }

        @Override
        public ParticleSpawn size(float size) {
            return size(size, size);
        }

        @Override
        public ParticleSpawn size(float start, float end) {
            data[base + 8] = start;
            data[base + 9] = end;
            return this;
        }

        @Override
        public ParticleSpawn rotation(float radians, float spinPerSecond) {
            data[base + 10] = radians;
            data[base + 11] = spinPerSecond;
            return this;
        }

        @Override
        public ParticleSpawn color(int rgb, float alpha) {
            // as it looks on screen: the frame particles draw onto is display-encoded (unlike Uniforms.color)
            return color(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
        }

        @Override
        public ParticleSpawn color(float r, float g, float b, float a) {
            data[base + 12] = r;
            data[base + 13] = g;
            data[base + 14] = b;
            data[base + 15] = a;
            return this;
        }

        @Override
        public ParticleSpawn data(float x, float y, float z, float w) {
            data[base + 16] = x;
            data[base + 17] = y;
            data[base + 18] = z;
            data[base + 19] = w;
            return this;
        }

        @Override
        public ParticleSpawn delay(float seconds) {
            data[base + 3] += Math.max(0f, seconds);
            return this;
        }

        @Override
        public ParticleSpawn axis(double x, double y, double z, float length) {
            data[base + 20] = (float) x;
            data[base + 21] = (float) y;
            data[base + 22] = (float) z;
            data[base + 23] = Math.max(0f, length);
            return this;
        }

        @Override
        public ParticleSpawn axis(Vec3 direction, float length) {
            return axis(direction.x, direction.y, direction.z, length);
        }

        @Override
        public ParticleSpawn orbit(float radiansPerSecond, float spinUp) {
            if (radiansPerSecond != 0f || spinUp != 0f) {
                toOrbitFrame();
            }
            data[base + 24] = radiansPerSecond;
            data[base + 25] = spinUp;
            return this;
        }

        @Override
        public ParticleSpawn pull(float blocksPerSecond) {
            if (blocksPerSecond != 0f) {
                toOrbitFrame();
            }
            data[base + 26] = blocksPerSecond;
            return this;
        }

        /**
         * An orbiting particle is stored relative to the orbit centre as it is now, and drawn round the centre as it
         * is then, so it moves with the axis (an aura round a walking player stays round them). Once per particle.
         */
        private void toOrbitFrame() {
            float[] d = data;
            if (d[base + 24] != 0f || d[base + 25] != 0f || d[base + 26] != 0f) {
                return;
            }
            d[base] -= orbitX();
            d[base + 1] -= orbitY();
            d[base + 2] -= orbitZ();
        }
    }

    private static final class EmitPointImpl implements FxEmitter.EmitPoint {
        int index;
        int count;
        double x;
        double y;
        double z;
        double dx;
        double dy;
        double dz;

        @Override
        public int index() {
            return index;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public float fraction() {
            return count > 1 ? index / (float) (count - 1) : 0f;
        }

        @Override
        public double x() {
            return x;
        }

        @Override
        public double y() {
            return y;
        }

        @Override
        public double z() {
            return z;
        }

        @Override
        public double dx() {
            return dx;
        }

        @Override
        public double dy() {
            return dy;
        }

        @Override
        public double dz() {
            return dz;
        }
    }

    /** A chain of ribbon points; the link is written only in RIBBON batches (elsewhere axis.x is the axis). */
    private final class RibbonImpl implements Ribbon {
        private int id = nextRibbon++;
        private int lastSlot = -1;

        @Override
        public ParticleSpawn spawn(Vec3 world) {
            return spawn(world.x, world.y, world.z);
        }

        @Override
        public ParticleSpawn spawn(double x, double y, double z) {
            int slot = head;
            ParticleSpawn s = ParticleBatch.this.spawn(x, y, z);
            if (facing == Facing.RIBBON) {
                int b = slot * FLOATS;
                data[b + 20] = id;
                data[b + 27] = lastSlot + 1;
            }
            lastSlot = slot;
            return s;
        }

        @Override
        public void cut() {
            id = nextRibbon++;
            lastSlot = -1;
        }
    }

    /** Longest gap a stream catches up on, seconds. */
    static final float STREAM_MAX_GAP = 0.25f;

    private final class StreamImpl implements Stream {
        private float rate;
        private float carry;
        private float lastTime = Float.NaN;
        private double lx;
        private double ly;
        private double lz;
        private final RibbonImpl link = new RibbonImpl();

        StreamImpl(float perSecond) {
            rate(perSecond);
        }

        @Override
        public Stream rate(float perSecond) {
            this.rate = Math.max(0f, perSecond);
            return this;
        }

        @Override
        public float rate() {
            return rate;
        }

        @Override
        public int emit(FxEmitter.Shape shape, Vec3 centre, double speed, @Nullable FxEmitter.EmitAction each) {
            return emit(shape, centre.x, centre.y, centre.z, speed, each);
        }

        @Override
        public int emit(FxEmitter.Shape shape, double x, double y, double z, double speed,
                        @Nullable FxEmitter.EmitAction each) {
            Objects.requireNonNull(shape, "shape");
            float now = now();
            if (Float.isNaN(lastTime) || now < lastTime) {
                restart(now, x, y, z);
                link.cut();
                return 0;
            }
            float dt = now - lastTime;
            if (dt <= 0f) {
                return 0;
            }
            double sx = lx;
            double sy = ly;
            double sz = lz;
            if (dt > STREAM_MAX_GAP) {
                // a long gap (paused, unloaded): flow on from near here instead of filling the gap all at once, and
                // don't join a ribbon across it
                link.cut();
                float keep = STREAM_MAX_GAP / dt;
                sx = x + (lx - x) * keep;
                sy = y + (ly - y) * keep;
                sz = z + (lz - z) * keep;
                dt = STREAM_MAX_GAP;
            }
            float from = now - dt;
            carry += rate * dt;
            int n = (int) carry;
            carry -= n;
            n = Math.min(n, capacityValue());
            for (int i = 0; i < n; i++) {
                float f = (i + 1f) / n; // the newest is born now, at the current centre
                emitOne(shape, sx + (x - sx) * f, sy + (y - sy) * f, sz + (z - sz) * f, i, n, speed,
                        from + dt * f, link, each);
            }
            lastTime = now;
            lx = x;
            ly = y;
            lz = z;
            return n;
        }

        private void restart(float now, double x, double y, double z) {
            lastTime = now;
            carry = 0f;
            lx = x;
            ly = y;
            lz = z;
        }

        @Override
        public void reset() {
            lastTime = Float.NaN;
            carry = 0f;
            link.cut();
        }
    }

}
