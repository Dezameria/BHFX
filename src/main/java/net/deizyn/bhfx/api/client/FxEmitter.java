package net.deizyn.bhfx.api.client;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * Where particles start and which way they head: spawn shapes for {@link FxParticles#emit} and
 * {@link FxParticles.Stream}. A shape gives each particle an offset from the emit centre and a unit direction
 * (outwards for spheres, rings and discs, inside the cone for cones), which {@code emit} turns into the velocity
 * {@code direction × speed}.
 * <p>
 * Shapes are small immutable values: keep them in {@code static final} fields. Shapes round an axis (ring, disc,
 * cylinder, cone, line) lie across / along {@link Shape#axis} (default up). {@link Shape#even} spaces the particles of
 * one emit evenly (ring angles, line steps, sphere and cone directions on a Fibonacci spiral) instead of at random.
 * {@link Shape#onGround} drops each point onto the terrain below it (or lifts it out of the terrain it is in).
 */
@ApiStatus.Experimental
public final class FxEmitter {
    private FxEmitter() {
    }

    /** One point; directions in every direction. */
    public static Shape point() {
        return new Shape(Kind.POINT, 0, 0, 0, 0, 1, 0, false);
    }

    /**
     * Finds the ground under a point for {@link Shape#onGround}: the top of the nearest block surface within
     * {@code search} blocks above or below y, or NaN.
     */
    @FunctionalInterface
    @ApiStatus.Internal
    public interface GroundProbe {
        double surface(double x, double y, double z, double search);
    }

    /** Anywhere inside a ball, heading out from its centre. */
    public static Shape sphere(double radius) {
        return new Shape(Kind.SPHERE, radius, 0, 0, 0, 1, 0, false);
    }

    /** On the surface of a ball, heading out. */
    public static Shape shell(double radius) {
        return new Shape(Kind.SHELL, radius, 0, 0, 0, 1, 0, false);
    }

    /** On a circle across the axis, heading out from its centre. */
    public static Shape ring(double radius) {
        return new Shape(Kind.RING, radius, 0, 0, 0, 1, 0, false);
    }

    /** Anywhere on a filled circle across the axis, heading out from its centre. */
    public static Shape disc(double radius) {
        return new Shape(Kind.DISC, radius, 0, 0, 0, 1, 0, false);
    }

    /** On the side of a cylinder standing on the centre along the axis ({@code height} up), heading out from it. */
    public static Shape cylinder(double radius, double height) {
        return new Shape(Kind.CYLINDER, radius, height, 0, 0, 1, 0, false);
    }

    /** From the centre, heading within {@code halfAngle} radians of the axis (π = every direction). */
    public static Shape cone(double halfAngle) {
        return new Shape(Kind.CONE, Mth.clamp(halfAngle, 0, Math.PI), 0, 0, 0, 1, 0, false);
    }

    /** On the segment from the centre to centre + {@code vector}, heading out across it. */
    public static Shape line(Vec3 vector) {
        return line(vector.x, vector.y, vector.z);
    }

    public static Shape line(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return length < 1e-9
                ? new Shape(Kind.LINE, 0, 0, 0, 0, 1, 0, false)
                : new Shape(Kind.LINE, length, 0, 0, x / length, y / length, z / length, false);
    }

    /** Anywhere inside a world-aligned box of these half sizes; directions in every direction. Ignores the axis. */
    public static Shape box(double halfX, double halfY, double halfZ) {
        return new Shape(Kind.BOX, halfX, halfY, halfZ, 0, 1, 0, false);
    }

    private enum Kind {
        POINT, SPHERE, SHELL, RING, DISC, CYLINDER, CONE, LINE, BOX
    }

    /** One spawn of an emit, as given to {@link EmitAction}. Reused for every particle: read it, don't keep it. */
    @ApiStatus.Experimental
    public interface EmitPoint {
        /** 0 .. count - 1 within this emit. */
        int index();

        int count();

        /** index / (count - 1): 0 for the first, 1 for the last (0 when there is one). */
        float fraction();

        /** World position the particle was spawned at. */
        double x();

        double y();

        double z();

        /** The shape's unit direction for this particle. */
        double dx();

        double dy();

        double dz();

        default Vec3 position() {
            return new Vec3(x(), y(), z());
        }

        default Vec3 direction() {
            return new Vec3(dx(), dy(), dz());
        }
    }

    /** Called for every particle of an emit, right after it was spawned and given its velocity; set the rest. */
    @FunctionalInterface
    @ApiStatus.Experimental
    public interface EmitAction {
        void accept(FxParticles.ParticleSpawn spawn, EmitPoint point);
    }

    @ApiStatus.Experimental
    public static final class Shape {
        private static final double GOLDEN = Math.PI * (3.0 - Math.sqrt(5.0));

        private final Kind kind;
        private final double a;
        private final double b;
        private final double c;
        private final double ax;
        private final double ay;
        private final double az;
        private final boolean even;
        private final double groundSearch;
        private final double groundLift;
        // two unit vectors across the axis
        private final double ux;
        private final double uy;
        private final double uz;
        private final double vx;
        private final double vy;
        private final double vz;

        private Shape(Kind kind, double a, double b, double c, double ax, double ay, double az, boolean even) {
            this(kind, a, b, c, ax, ay, az, even, 0, 0);
        }

        private Shape(Kind kind, double a, double b, double c, double ax, double ay, double az, boolean even,
                      double groundSearch, double groundLift) {
            this.groundSearch = Math.max(0, groundSearch);
            this.groundLift = groundLift;
            this.kind = kind;
            this.a = Math.max(0, a);
            this.b = Math.max(0, b);
            this.c = Math.max(0, c);
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.even = even;
            // u: any unit vector across the axis, v = axis × u
            double px = Math.abs(ay) < 0.9 ? 0 : 1;
            double py = Math.abs(ay) < 0.9 ? 1 : 0;
            // u = normalize(p × axis)
            double x = py * az;
            double y = -px * az;
            double z = px * ay - py * ax;
            double l = Math.sqrt(x * x + y * y + z * z);
            ux = x / l;
            uy = y / l;
            uz = z / l;
            vx = ay * uz - az * uy;
            vy = az * ux - ax * uz;
            vz = ax * uy - ay * ux;
        }

        /** The same shape turned to lie round / along {@code direction} (any length). */
        public Shape axis(double x, double y, double z) {
            double l = Math.sqrt(x * x + y * y + z * z);
            if (l < 1e-9) {
                return this;
            }
            return new Shape(kind, a, b, c, x / l, y / l, z / l, even, groundSearch, groundLift);
        }

        public Shape axis(Vec3 direction) {
            return axis(direction.x, direction.y, direction.z);
        }

        /** The same shape with the particles of one emit spaced evenly instead of at random. */
        public Shape even() {
            return even ? this : new Shape(kind, a, b, c, ax, ay, az, true, groundSearch, groundLift);
        }

        /**
         * The same shape with every point moved onto the block surface nearest below it (or up out of the block it is
         * in), searching {@code search} blocks each way, then raised by {@code lift} (e.g. 0.03 for flat decals).
         * Points with no surface in reach stay where they are. Fluid surfaces count (a point over water lands on the
         * water, not the bed under it, which the water would hide); grass, flowers and other blocks without collision
         * don't.
         * Reads the client world at emit time (a few block lookups per particle).
         */
        public Shape onGround(double search, double lift) {
            return new Shape(kind, a, b, c, ax, ay, az, even, search, lift);
        }

        public Shape onGround(double search) {
            return onGround(search, 0);
        }

        /** Search distance of {@link #onGround}, 0 when off. */
        public double groundSearch() {
            return groundSearch;
        }

        public double groundLift() {
            return groundLift;
        }

        /**
         * Pick one particle of an emit of {@code count}: writes the offset from the centre into {@code out[0..2]} and
         * the unit direction into {@code out[3..5]}. Usable outside {@code emit}, e.g. to place things yourself.
         */
        public void sample(RandomSource random, int index, int count, double[] out) {
            double t = even && count > 0 ? (index + 0.5) / count : random.nextDouble();
            switch (kind) {
                case POINT -> {
                    sphereDir(random, index, count, out);
                    out[0] = out[1] = out[2] = 0;
                }
                case SPHERE -> {
                    sphereDir(random, index, count, out);
                    double r = a * Math.cbrt(random.nextDouble());
                    out[0] = out[3] * r;
                    out[1] = out[4] * r;
                    out[2] = out[5] * r;
                }
                case SHELL -> {
                    sphereDir(random, index, count, out);
                    out[0] = out[3] * a;
                    out[1] = out[4] * a;
                    out[2] = out[5] * a;
                }
                case RING, DISC, CYLINDER -> {
                    double angle = t * Math.PI * 2;
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    double dx = ux * cos + vx * sin;
                    double dy = uy * cos + vy * sin;
                    double dz = uz * cos + vz * sin;
                    double r = kind == Kind.DISC ? a * Math.sqrt(random.nextDouble()) : a;
                    double h = kind == Kind.CYLINDER ? b * random.nextDouble() : 0;
                    out[0] = dx * r + ax * h;
                    out[1] = dy * r + ay * h;
                    out[2] = dz * r + az * h;
                    out[3] = dx;
                    out[4] = dy;
                    out[5] = dz;
                }
                case CONE -> {
                    double cosT;
                    double phi;
                    if (even && count > 0) {
                        cosT = 1 - (index + 0.5) / count * (1 - Math.cos(a));
                        phi = index * GOLDEN;
                    } else {
                        cosT = 1 - random.nextDouble() * (1 - Math.cos(a));
                        phi = random.nextDouble() * Math.PI * 2;
                    }
                    double sinT = Math.sqrt(Math.max(0, 1 - cosT * cosT));
                    double cp = Math.cos(phi) * sinT;
                    double sp = Math.sin(phi) * sinT;
                    out[0] = out[1] = out[2] = 0;
                    out[3] = ax * cosT + ux * cp + vx * sp;
                    out[4] = ay * cosT + uy * cp + vy * sp;
                    out[5] = az * cosT + uz * cp + vz * sp;
                }
                case LINE -> {
                    double along = a * t;
                    double angle = random.nextDouble() * Math.PI * 2;
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    out[0] = ax * along;
                    out[1] = ay * along;
                    out[2] = az * along;
                    out[3] = ux * cos + vx * sin;
                    out[4] = uy * cos + vy * sin;
                    out[5] = uz * cos + vz * sin;
                }
                case BOX -> {
                    out[0] = (random.nextDouble() * 2 - 1) * a;
                    out[1] = (random.nextDouble() * 2 - 1) * b;
                    out[2] = (random.nextDouble() * 2 - 1) * c;
                    sphereDir(random, index, count, out);
                }
            }
        }

        /** A unit direction into out[3..5]: Fibonacci spiral when even, else uniform at random. */
        private void sphereDir(RandomSource random, int index, int count, double[] out) {
            double y;
            double phi;
            if (even && count > 0) {
                y = 1 - 2 * (index + 0.5) / count;
                phi = index * GOLDEN;
            } else {
                y = random.nextDouble() * 2 - 1;
                phi = random.nextDouble() * Math.PI * 2;
            }
            double r = Math.sqrt(Math.max(0, 1 - y * y));
            out[3] = Math.cos(phi) * r;
            out[4] = y;
            out[5] = Math.sin(phi) * r;
        }
    }
}
