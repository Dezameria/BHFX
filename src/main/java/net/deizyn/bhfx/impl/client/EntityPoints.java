package net.deizyn.bhfx.impl.client;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.deizyn.bhfx.api.client.BodyPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Named points on entities (a bone of a GeckoLib model, a weapon's tip), reported by renderers from inside the world's
 * entity pass ({@code BHFxClient.reportPoint}) and read back by effects. Separate from {@link BodyParts}: asking for a
 * point never makes bhfx capture body parts, and the reverse. Only entities asked about recently are wanted, so a
 * reporter costs one map lookup per entity render otherwise. Nothing is estimated: a point that was not reported
 * recently is simply absent. Render thread only.
 */
public final class EntityPoints {
    /** A report older than this many frames counts as gone. */
    private static final int FRESH_FRAMES = 3;
    /** Keep taking reports for an entity while it was asked about within this many frames. */
    private static final int WANT_FRAMES = 200;
    /** Most points kept per entity (a model with more bones keeps the first ones reported each frame). */
    static final int MAX_POINTS = 128;

    private static final Int2ObjectOpenHashMap<Map<String, Point>> POINTS = new Int2ObjectOpenHashMap<>();
    private static final Int2LongOpenHashMap WANTED = new Int2LongOpenHashMap();
    private static final Matrix4f SCRATCH = new Matrix4f();
    private static final Vector3f V = new Vector3f();
    private static long lastPrune;

    private EntityPoints() {
    }

    private record Point(BodyPose pose, long frame) {
    }

    /** Whether renders running now should report {@code entity}'s points. */
    public static boolean wants(Entity entity) {
        if (!BodyParts.capturingWorld()) {
            return false;
        }
        long asked = WANTED.getOrDefault(entity.getId(), Long.MIN_VALUE);
        return asked != Long.MIN_VALUE && BodyParts.frame() - asked <= WANT_FRAMES;
    }

    /**
     * Record point {@code name} of {@code entity} from a pose-stack matrix moved to it. Its axes: forward = the
     * matrix's -z, up = +y, right = +x (Blockbench and GeckoLib bone space). Ignored unless {@link #wants}.
     */
    public static void report(Entity entity, String name, Matrix4f poseOnStack) {
        if (name == null || !wants(entity)) {
            return;
        }
        Map<String, Point> points = POINTS.computeIfAbsent(entity.getId(), id -> new HashMap<>());
        if (points.size() >= MAX_POINTS && !points.containsKey(name)) {
            return;
        }
        BodyParts.toWorldRotation(poseOnStack, SCRATCH);
        SCRATCH.getTranslation(V);
        Vec3 camera = BodyParts.camera();
        Vec3 position = new Vec3(camera.x + V.x, camera.y + V.y, camera.z + V.z);
        BodyPose pose = new BodyPose(position, axis(0f, 0f, -1f), axis(0f, 1f, 0f), axis(1f, 0f, 0f), true);
        points.put(name, new Point(pose, BodyParts.frame()));
        prune();
    }

    /** Record a point already in world space (for reporters that work out world positions themselves). */
    public static void report(Entity entity, String name, BodyPose worldPose) {
        if (name == null || worldPose == null || !wants(entity)) {
            return;
        }
        Map<String, Point> points = POINTS.computeIfAbsent(entity.getId(), id -> new HashMap<>());
        if (points.size() >= MAX_POINTS && !points.containsKey(name)) {
            return;
        }
        points.put(name, new Point(worldPose, BodyParts.frame()));
        prune();
    }

    private static Vec3 axis(float x, float y, float z) {
        SCRATCH.transformDirection(V.set(x, y, z));
        float len = V.length();
        return len < 1e-6f ? Vec3.ZERO : new Vec3(V.x / len, V.y / len, V.z / len);
    }

    /** Point {@code name} of {@code entity} if it was reported in the last few frames. Asking makes bhfx want it. */
    public static Optional<BodyPose> point(Entity entity, String name) {
        WANTED.put(entity.getId(), BodyParts.frame());
        Map<String, Point> points = POINTS.get(entity.getId());
        if (points == null || entity.isRemoved()) {
            return Optional.empty();
        }
        Point p = points.get(name);
        return p != null && BodyParts.frame() - p.frame() <= FRESH_FRAMES ? Optional.of(p.pose()) : Optional.empty();
    }

    /** Every point of {@code entity} reported in the last few frames, by name. Asking makes bhfx want them. */
    public static Map<String, BodyPose> points(Entity entity) {
        WANTED.put(entity.getId(), BodyParts.frame());
        Map<String, Point> points = POINTS.get(entity.getId());
        if (points == null || entity.isRemoved()) {
            return Collections.emptyMap();
        }
        Map<String, BodyPose> out = new LinkedHashMap<>();
        long now = BodyParts.frame();
        points.forEach((name, p) -> {
            if (now - p.frame() <= FRESH_FRAMES) {
                out.put(name, p.pose());
            }
        });
        return out;
    }

    private static void prune() {
        long now = BodyParts.frame();
        if (now - lastPrune < 256) {
            return;
        }
        lastPrune = now;
        POINTS.int2ObjectEntrySet().removeIf(e -> {
            e.getValue().values().removeIf(p -> now - p.frame() > WANT_FRAMES);
            return e.getValue().isEmpty();
        });
        WANTED.int2LongEntrySet().removeIf(e -> now - e.getLongValue() > WANT_FRAMES);
    }

    /** World change or logout: entity ids are reused. */
    static void clear() {
        POINTS.clear();
        WANTED.clear();
    }
}
