package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * A place with its own three axes: a bone, a hand, the base of a shape. Build chains of them in Java (each joint
 * {@link #moved} along the bone and {@link #turnedX turned}), write them into an {@link FxData} with
 * {@link FxData#setFrame}, and measure shapes in them in the shader ({@code fxFrame}, {@code fxToLocal} in
 * {@code bhfx:data.glsl}).
 * <p>
 * The axes should stay unit length and at right angles to each other (everything here keeps them so). Turns are given
 * in the frame's own axes, so they mean the same whichever way the axes are handed: {@link #turnedX} turns y towards
 * z, {@link #turnedY} z towards x, {@link #turnedZ} x towards y.
 * <p>
 * Positions are whatever space you build in (world space is easiest). Shaders work camera-relative: write
 * {@code frame.rel(ctx)} (or {@code relativeTo(camera)}), not the world frame.
 */
@ApiStatus.Experimental
public record FxFrame(Vec3 origin, Vec3 x, Vec3 y, Vec3 z) {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    /** At {@code origin} with the world's axes. */
    public static FxFrame at(Vec3 origin) {
        return new FxFrame(origin, X, Y, Z);
    }

    /** A body part's frame: x = its right, y = its up, z = its forward (see {@link BodyPose}). */
    public static FxFrame of(BodyPose pose) {
        return new FxFrame(pose.position(), pose.right(), pose.up(), pose.forward());
    }

    /**
     * At {@code origin}, z along {@code forward}, y as close to {@code up} as it can be at right angles to z, and x to
     * the right (forward × up), the same way round as {@link BodyPose}: facing +z with y up puts x at -x.
     */
    public static FxFrame looking(Vec3 origin, Vec3 forward, Vec3 up) {
        Vec3 z = unit(forward, Z);
        Vec3 x = unit(z.cross(up), z.cross(Math.abs(z.y) < 0.99 ? Y : X));
        Vec3 y = x.cross(z);
        return new FxFrame(origin, x, y, z);
    }

    /** A point given in this frame's coordinates. */
    public Vec3 point(double lx, double ly, double lz) {
        return origin.add(dir(lx, ly, lz));
    }

    /** A direction given in this frame's coordinates. */
    public Vec3 dir(double lx, double ly, double lz) {
        return new Vec3(x.x * lx + y.x * ly + z.x * lz, x.y * lx + y.y * ly + z.y * lz, x.z * lx + y.z * ly + z.z * lz);
    }

    /** {@code p} in this frame's coordinates. */
    public Vec3 toLocal(Vec3 p) {
        Vec3 q = p.subtract(origin);
        return new Vec3(q.dot(x), q.dot(y), q.dot(z));
    }

    /** The same axes, the origin moved by (lx, ly, lz) in this frame's coordinates. */
    public FxFrame moved(double lx, double ly, double lz) {
        return new FxFrame(point(lx, ly, lz), x, y, z);
    }

    /** The same axes at another origin. */
    public FxFrame withOrigin(Vec3 newOrigin) {
        return new FxFrame(newOrigin, x, y, z);
    }

    /** Turned round its own x axis by {@code angle} radians: y towards z (a finger curling, a nod). */
    public FxFrame turnedX(double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new FxFrame(origin, x, y.scale(c).add(z.scale(s)), z.scale(c).subtract(y.scale(s)));
    }

    /** Turned round its own y axis: z towards x (a turn left or right). */
    public FxFrame turnedY(double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new FxFrame(origin, x.scale(c).subtract(z.scale(s)), y, z.scale(c).add(x.scale(s)));
    }

    /** Turned round its own z axis: x towards y (a roll). */
    public FxFrame turnedZ(double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new FxFrame(origin, x.scale(c).add(y.scale(s)), y.scale(c).subtract(x.scale(s)), z);
    }

    /** Turned round a world-space unit axis through its origin by {@code angle} radians (right-hand rule). */
    public FxFrame turned(Vec3 axis, double angle) {
        return new FxFrame(origin, rotate(x, axis, angle), rotate(y, axis, angle), rotate(z, axis, angle));
    }

    /** This frame with its origin made relative to the camera, for the shader. */
    public FxFrame rel(FrameContext frame) {
        return withOrigin(frame.rel(origin));
    }

    /** This frame with its origin made relative to {@code camera}. */
    public FxFrame relativeTo(Vec3 camera) {
        return withOrigin(origin.subtract(camera));
    }

    /**
     * Where the middle joint of a two-bone limb goes (an elbow, a knee): the limb runs from {@code root} with bones
     * {@code upper} and {@code lower} long and its end reaching for {@code target}, bending towards {@code pole}. A
     * target out of reach is reached for in a straight line; one too close is held at the shortest the limb can fold.
     */
    public static Vec3 twoBoneJoint(Vec3 root, Vec3 target, double upper, double lower, Vec3 pole) {
        Vec3 to = target.subtract(root);
        double len = to.length();
        Vec3 dir = len > 1e-9 ? to.scale(1.0 / len) : Y;
        double d = Math.max(Math.abs(upper - lower) + 1e-6, Math.min(len, upper + lower - 1e-6));
        double along = (upper * upper - lower * lower + d * d) / (2.0 * d);
        double side = Math.sqrt(Math.max(0.0, upper * upper - along * along));
        Vec3 p = pole.subtract(root);
        Vec3 bend = p.subtract(dir.scale(p.dot(dir)));
        if (bend.lengthSqr() < 1e-12) {
            bend = dir.cross(Math.abs(dir.y) < 0.99 ? Y : X);
        }
        return root.add(dir.scale(along)).add(bend.normalize().scale(side));
    }

    private static Vec3 rotate(Vec3 v, Vec3 k, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1.0 - c)));
    }

    private static Vec3 unit(Vec3 v, Vec3 fallback) {
        double l = v.length();
        if (l > 1e-9) {
            return v.scale(1.0 / l);
        }
        double fl = fallback.length();
        return fl > 1e-9 ? fallback.scale(1.0 / fl) : X;
    }
}
