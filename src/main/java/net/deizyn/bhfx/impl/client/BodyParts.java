package net.deizyn.bhfx.impl.client;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.deizyn.bhfx.api.BodyPart;
import net.deizyn.bhfx.api.client.BodyPose;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where body parts are, per entity. Entity renders write the posed model's part matrices while the world's entity pass
 * runs ({@link BodyPartLayer} for vanilla models, {@code BHFxClient.reportBodyPart} for renderers that replace them,
 * such as Epic Fight); queries read the newest capture and fall back to an estimate from the entity's size and body
 * rotation. Only entities someone asked about recently are captured, so the layer costs a map lookup otherwise.
 * Render thread only.
 */
public final class BodyParts {
    private static final BodyPart[] PARTS = BodyPart.values();
    /** A capture older than this many frames counts as gone (entity off screen, culled, first person). */
    private static final int FRESH_FRAMES = 3;
    /** Capture an entity while it was asked about within this many frames. */
    private static final int WANT_FRAMES = 200;

    private static final Int2ObjectOpenHashMap<Capture> CAPTURES = new Int2ObjectOpenHashMap<>();
    private static final Int2LongOpenHashMap WANTED = new Int2LongOpenHashMap();
    private static final Matrix4f VIEW_INVERSE = new Matrix4f();
    private static final Matrix4f SCRATCH = new Matrix4f();
    private static final Vector3f V = new Vector3f();
    private static Vec3 camera = Vec3.ZERO;
    private static long frame;
    private static boolean capturing;

    private BodyParts() {
    }

    private static final class Capture {
        final long[] frame = new long[PARTS.length];
        final boolean[] external = new boolean[PARTS.length];
        final BodyPose[] pose = new BodyPose[PARTS.length];

        Capture() {
            java.util.Arrays.fill(frame, Long.MIN_VALUE / 2);
        }
    }

    // ------------------------------------------------------------------ capture window

    /**
     * The world's entity pass is about to run: {@code view} is the camera-rotation matrix entity renders start from.
     * Called after the mirror reflections at {@code AFTER_SOLID_BLOCKS}, so reflections never write captures.
     */
    static void openWindow(Matrix4f view, Camera cam) {
        frame++;
        VIEW_INVERSE.set(view).invert();
        camera = cam.getPosition();
        capturing = true;
        if ((frame & 255) == 0) {
            prune();
        }
    }

    /** Any later stage (and the start of the next frame) ends it: GUI and hand renders must never write captures. */
    static void closeWindow() {
        capturing = false;
    }

    /** True while the world's entity pass runs (not mirror reflections): when {@link EntityPoints} takes reports too. */
    static boolean capturingWorld() {
        return capturing && !ClientFxManager.drawingMirrorReflection();
    }

    /** This frame's number (one per entity pass), shared with {@link EntityPoints}. */
    static long frame() {
        return frame;
    }

    /** A pose-stack matrix from an entity render (camera-relative, camera-rotated) to world space, into {@code out}. */
    static Matrix4f toWorldRotation(Matrix4f poseOnStack, Matrix4f out) {
        return out.set(VIEW_INVERSE).mul(poseOnStack);
    }

    static Vec3 camera() {
        return camera;
    }

    /** Whether entity renders running now should report {@code entity}'s parts. */
    public static boolean wants(Entity entity) {
        if (!capturing || ClientFxManager.drawingMirrorReflection()) {
            return false;
        }
        long asked = WANTED.getOrDefault(entity.getId(), Long.MIN_VALUE);
        return asked != Long.MIN_VALUE && frame - asked <= WANT_FRAMES;
    }

    /**
     * Record {@code part} of {@code entity} from a model-space matrix as it sits on the entity render's pose stack
     * (camera-relative, camera-rotated). The point is the matrix's origin. Ignored outside the world's entity pass.
     * {@code external}: from a renderer that replaced the vanilla model; vanilla captures then leave it alone this frame.
     */
    public static void report(Entity entity, BodyPart part, Matrix4f poseOnStack, boolean external) {
        if (!wants(entity)) {
            return;
        }
        Capture c = CAPTURES.computeIfAbsent(entity.getId(), id -> new Capture());
        int i = part.ordinal();
        if (!external && c.frame[i] == frame && c.external[i]) {
            return;
        }
        SCRATCH.set(VIEW_INVERSE).mul(poseOnStack);
        SCRATCH.getTranslation(V);
        Vec3 position = new Vec3(camera.x + V.x, camera.y + V.y, camera.z + V.z);
        // model space: y down, front is -z, the entity's right is -x (vanilla's right arm sits at x = -5)
        c.pose[i] = new BodyPose(position, axis(0f, 0f, -1f), axis(0f, -1f, 0f), axis(-1f, 0f, 0f), true);
        c.frame[i] = frame;
        c.external[i] = external;
    }

    private static Vec3 axis(float x, float y, float z) {
        SCRATCH.transformDirection(V.set(x, y, z));
        float len = V.length();
        return len < 1e-6f ? Vec3.ZERO : new Vec3(V.x / len, V.y / len, V.z / len);
    }

    // ------------------------------------------------------------------ queries

    /** The pose of {@code part} on {@code entity}: this frame's capture when there is one, else an estimate. */
    public static BodyPose pose(Entity entity, BodyPart part, float partial) {
        WANTED.put(entity.getId(), frame);
        Capture c = CAPTURES.get(entity.getId());
        if (c != null && !entity.isRemoved()) {
            if (part == BodyPart.FEET) {
                // halfway between the soles, with the torso's axes (the soles tilt with each step)
                BodyPose l = fresh(c, BodyPart.LEFT_FOOT);
                BodyPose r = fresh(c, BodyPart.RIGHT_FOOT);
                if (l != null && r != null) {
                    BodyPose body = fresh(c, BodyPart.BODY);
                    BodyPose axes = body != null ? body : l;
                    return new BodyPose(l.position().add(r.position()).scale(0.5), axes.forward(), axes.up(),
                            axes.right(), true);
                }
            } else {
                BodyPose p = fresh(c, part);
                if (p != null) {
                    return p;
                }
            }
        }
        return estimate(entity, part, partial);
    }

    private static BodyPose fresh(Capture c, BodyPart part) {
        int i = part.ordinal();
        return c.pose[i] != null && frame - c.frame[i] <= FRESH_FRAMES ? c.pose[i] : null;
    }

    // standing 1.8 x 0.6 humanoid, blocks: (right, up, forward) from the feet
    private static final double[][] OFFSETS = {
            {0.0, 1.75, 0.0},    // HEAD
            {0.0, 1.125, 0.0},   // BODY
            {-0.375, 1.5, 0.0},  // LEFT_SHOULDER
            {0.375, 1.5, 0.0},   // RIGHT_SHOULDER
            {-0.375, 1.125, 0.0},// LEFT_ARM
            {0.375, 1.125, 0.0}, // RIGHT_ARM
            {-0.375, 0.75, 0.0}, // LEFT_HAND
            {0.375, 0.75, 0.0},  // RIGHT_HAND
            {-0.12, 0.375, 0.0}, // LEFT_LEG
            {0.12, 0.375, 0.0},  // RIGHT_LEG
            {-0.12, 0.0, 0.0},   // LEFT_FOOT
            {0.12, 0.0, 0.0},    // RIGHT_FOOT
            {0.0, 0.0, 0.0},     // FEET
    };

    /** From the entity's size and body rotation; in first person the hands and arms sit where the view shows them. */
    static BodyPose estimate(Entity entity, BodyPart part, float partial) {
        Minecraft mc = Minecraft.getInstance();
        boolean limb = part == BodyPart.LEFT_HAND || part == BodyPart.RIGHT_HAND
                || part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM
                || part == BodyPart.LEFT_SHOULDER || part == BodyPart.RIGHT_SHOULDER;
        if (limb && entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            return firstPerson(mc.gameRenderer.getMainCamera(), part);
        }
        float yaw = entity instanceof LivingEntity living
                ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot)
                : entity.getViewYRot(partial);
        double rad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
        Vec3 right = new Vec3(-Math.cos(rad), 0.0, -Math.sin(rad));
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        double h = entity.getBbHeight() / 1.8;
        double w = entity.getBbWidth() / 0.6;
        double[] o = OFFSETS[part.ordinal()];
        Vec3 base = entity.getPosition(partial);
        Vec3 at = base.add(right.scale(o[0] * w)).add(0.0, o[1] * h, 0.0).add(forward.scale(o[2] * w));
        if (part == BodyPart.HEAD) {
            Vec3 look = entity.getViewVector(partial);
            Vec3 headRight = look.cross(up).lengthSqr() < 1e-6 ? right : look.cross(up).normalize();
            return new BodyPose(at, look, headRight.cross(look).normalize(), headRight, false);
        }
        return new BodyPose(at, forward, up, right, false);
    }

    private static BodyPose firstPerson(Camera cam, BodyPart part) {
        Vec3 look = new Vec3(cam.getLookVector());
        Vec3 up = new Vec3(cam.getUpVector());
        Vec3 right = new Vec3(cam.getLeftVector()).scale(-1.0);
        boolean hand = part == BodyPart.LEFT_HAND || part == BodyPart.RIGHT_HAND;
        boolean shoulder = part == BodyPart.LEFT_SHOULDER || part == BodyPart.RIGHT_SHOULDER;
        double side = part == BodyPart.RIGHT_HAND || part == BodyPart.RIGHT_ARM || part == BodyPart.RIGHT_SHOULDER
                ? 1.0 : -1.0;
        Vec3 at = cam.getPosition()
                .add(look.scale(hand ? 0.55 : shoulder ? 0.05 : 0.3))
                .add(right.scale(side * (hand ? 0.38 : 0.42)))
                .add(up.scale(hand ? -0.32 : shoulder ? -0.45 : -0.5));
        return new BodyPose(at, look, up, right, false);
    }

    // ------------------------------------------------------------------ housekeeping

    private static void prune() {
        CAPTURES.int2ObjectEntrySet().removeIf(e -> {
            long newest = Long.MIN_VALUE;
            for (long f : e.getValue().frame) {
                newest = Math.max(newest, f);
            }
            return frame - newest > WANT_FRAMES;
        });
        WANTED.int2LongEntrySet().removeIf(e -> frame - e.getLongValue() > WANT_FRAMES);
    }

    /** World change or logout: entity ids are reused. */
    static void clear() {
        CAPTURES.clear();
        WANTED.clear();
        capturing = false;
    }
}
