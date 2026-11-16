package net.deizyn.bhfx.impl.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;
import net.deizyn.bhfx.api.client.Uniforms;
import net.deizyn.bhfx.api.client.FxFrame;
import net.deizyn.bhfx.api.client.FxModel;
import net.deizyn.bhfx.api.client.FxPose;
import net.deizyn.bhfx.api.client.FxData;
import net.deizyn.bhfx.impl.render.DataTable;
import net.deizyn.bhfx.impl.render.ModelDraw;
import net.deizyn.bhfx.impl.render.ParticleBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Passes collected from every effect in one frame. */
public final class PassListImpl implements PassList {
    /**
     * {@code bx, by, bz} = camera-relative centre of the bounds; {@code radius <= 0} = whole screen (for particles: no
     * bounds given, the batch's own sphere is used). {@code particles} is set for a particle batch, drawn at {@code time};
     * {@code model} for a model ({@code shader} is then its material).
     */
    public record Entry(ResourceLocation shader, int order, int seq, double distance, Consumer<Uniforms> setup,
                        double bx, double by, double bz, double radius, @Nullable ParticleBatch particles, float time,
                        float presence, @Nullable ModelDraw model) {
        /** Not a full-screen pass: a particle batch or a model. */
        public boolean drawsGeometry() {
            return particles != null || model != null;
        }
    }

    private static final Comparator<Entry> DRAW_ORDER = Comparator.comparingInt(Entry::order).thenComparingInt(Entry::seq);
    private static final Comparator<Entry> KEEP_ORDER =
            Comparator.comparingInt(Entry::order).thenComparingDouble(Entry::distance).thenComparingInt(Entry::seq);

    private static final net.minecraft.resources.ResourceLocation LIGHTS =
            new net.minecraft.resources.ResourceLocation("bhfx", "lights");

    private final List<Entry> entries = new ArrayList<>();
    /** This frame's lights, two vec4 each: (camera-relative centre, radius), (linear rgb * strength, unused). */
    private final float[] lights = new float[MAX_LIGHTS * 8];
    private int lightCount;
    private final Consumer<Uniforms> lightSetup = u -> u.floats("Lights", lights).f1("LightCount", lightCount);
    private final List<Entry> kept = new ArrayList<>();
    private double distance;
    private float presence = 1f;
    private Vec3 camera = Vec3.ZERO;

    /** Start a frame; bounds are stored relative to {@code camera}. */
    void clear(Vec3 camera) {
        entries.clear();
        lightCount = 0;
        this.camera = camera;
        this.presence = 1f;
    }

    void currentDistance(double distance) {
        this.distance = distance;
    }

    /** How much of the current effect shows (0..1): it fades in when built late and out when hidden. */
    void currentPresence(float presence) {
        this.presence = presence;
    }

    boolean isEmpty() {
        return entries.isEmpty() && lightCount == 0;
    }

    int lightCount() {
        return lightCount;
    }

    @Override
    public void light(Vec3 position, int rgb, float strength, float radius) {
        float s = strength * presence;
        if (position == null || !(s > 0.001f) || !(radius > 0.01f)) {
            return;
        }
        float x = (float) (position.x - camera.x);
        float y = (float) (position.y - camera.y);
        float z = (float) (position.z - camera.z);
        int slot = lightCount;
        if (slot >= MAX_LIGHTS) {
            // full: the new light takes the place of the one farthest from the camera, when it is nearer than that
            float far = reachOf(x, y, z, radius);
            slot = -1;
            for (int i = 0; i < MAX_LIGHTS; i++) {
                float r = reachOf(lights[i * 8], lights[i * 8 + 1], lights[i * 8 + 2], lights[i * 8 + 3]);
                if (r > far) {
                    far = r;
                    slot = i;
                }
            }
            if (slot < 0) {
                return;
            }
        } else {
            lightCount++;
        }
        int b = slot * 8;
        lights[b] = x;
        lights[b + 1] = y;
        lights[b + 2] = z;
        lights[b + 3] = radius;
        lights[b + 4] = linear(rgb >> 16) * s;
        lights[b + 5] = linear(rgb >> 8) * s;
        lights[b + 6] = linear(rgb) * s;
        lights[b + 7] = 0f;
    }

    /** Distance from the camera to the nearest point a light reaches (0 inside it). */
    private static float reachOf(float x, float y, float z, float radius) {
        return Math.max(0f, (float) Math.sqrt(x * x + y * y + z * z) - radius);
    }

    private static float linear(int channel) {
        return (float) Math.pow((channel & 0xFF) / 255.0, 2.2);
    }

    /** The built-in lights pass, bounded by a sphere round every light; drawn before everything else. */
    private Entry lightsEntry() {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < lightCount; i++) {
            int b = i * 8;
            float r = lights[b + 3];
            minX = Math.min(minX, lights[b] - r);
            maxX = Math.max(maxX, lights[b] + r);
            minY = Math.min(minY, lights[b + 1] - r);
            maxY = Math.max(maxY, lights[b + 1] + r);
            minZ = Math.min(minZ, lights[b + 2] - r);
            maxZ = Math.max(maxZ, lights[b + 2] + r);
        }
        double hx = (maxX - minX) * 0.5, hy = (maxY - minY) * 0.5, hz = (maxZ - minZ) * 0.5;
        return new Entry(LIGHTS, Integer.MIN_VALUE, -1, 0.0, lightSetup, minX + hx, minY + hy, minZ + hz,
                Math.sqrt(hx * hx + hy * hy + hz * hz), null, 0f, 1f, null);
    }

    @Override
    public void add(ResourceLocation shader, Consumer<Uniforms> setup) {
        add(shader, PassOptions.DEFAULT, setup);
    }

    @Override
    public void add(ResourceLocation shader, PassOptions options, Consumer<Uniforms> setup) {
        PassOptions.Bounds b = options.bounds();
        if (b == null) {
            entries.add(new Entry(shader, options.order(), entries.size(), distance, setup, 0, 0, 0, 0, null, 0f, presence,
                    null));
        } else {
            Vec3 c = b.center();
            entries.add(new Entry(shader, options.order(), entries.size(), distance, setup,
                    c.x - camera.x, c.y - camera.y, c.z - camera.z, b.radius(), null, 0f, presence, null));
        }
    }

    @Override
    public void particles(FxParticles particles, PassOptions options, Consumer<Uniforms> setup) {
        if (!(particles instanceof ParticleBatch batch) || batch.closed()) {
            return;
        }
        float now = batch.now();
        if (batch.idleAt(now)) {
            return;
        }
        PassOptions.Bounds b = options.bounds();
        double bx = 0;
        double by = 0;
        double bz = 0;
        double radius = 0;
        if (b != null) {
            bx = b.center().x - camera.x;
            by = b.center().y - camera.y;
            bz = b.center().z - camera.z;
            radius = b.radius();
        }
        entries.add(new Entry(batch.material(), options.order(), entries.size(), distance, setup, bx, by, bz, radius,
                batch, now, presence, null));
    }

    @Override
    public void model(FxModel model, FxFrame frame, float scale, @Nullable FxPose pose, ResourceLocation material,
                      PassOptions options, Consumer<Uniforms> setup) {
        if (model == null || frame == null || material == null || !(scale > 0f)) {
            return;
        }
        Vec3 o = frame.origin();
        float[] axes = {
                (float) (frame.x().x * scale), (float) (frame.x().y * scale), (float) (frame.x().z * scale),
                (float) (frame.y().x * scale), (float) (frame.y().y * scale), (float) (frame.y().z * scale),
                (float) (frame.z().x * scale), (float) (frame.z().y * scale), (float) (frame.z().z * scale)};
        float light = 1f;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            light = level.getMaxLocalRawBrightness(BlockPos.containing(o)) / 15f;
        }
        ModelDraw draw = new ModelDraw(model, (float) (o.x - camera.x), (float) (o.y - camera.y), (float) (o.z - camera.z),
                axes, scale, light, pose == null || pose.isEmpty() ? null : pose);
        PassOptions.Bounds b = options.bounds();
        double bx = 0;
        double by = 0;
        double bz = 0;
        double radius = 0;
        if (b != null) {
            bx = b.center().x - camera.x;
            by = b.center().y - camera.y;
            bz = b.center().z - camera.z;
            radius = b.radius();
        }
        entries.add(new Entry(material, options.order(), entries.size(), distance, setup, bx, by, bz, radius, null, 0f,
                presence, draw));
    }

    @Override
    public void models(FxModel model, FxData instances, int count, @Nullable FxPose pose, ResourceLocation material,
                       PassOptions options, Consumer<Uniforms> setup) {
        if (model == null || material == null || !(instances instanceof DataTable table) || count <= 0) {
            return;
        }
        int n = Math.min(count, table.size() / 4);
        if (n <= 0) {
            return;
        }
        PassOptions.Bounds b = options.bounds();
        double bx = 0;
        double by = 0;
        double bz = 0;
        double radius = 0;
        float light = 1f;
        Vec3 lightAt = b != null ? b.center() : camera;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            light = level.getMaxLocalRawBrightness(BlockPos.containing(lightAt)) / 15f;
        }
        if (b != null) {
            bx = b.center().x - camera.x;
            by = b.center().y - camera.y;
            bz = b.center().z - camera.z;
            radius = b.radius();
        }
        ModelDraw draw = new ModelDraw(model, 0f, 0f, 0f, new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, 1f, light, table, n,
                pose == null || pose.isEmpty() ? null : pose);
        entries.add(new Entry(material, options.order(), entries.size(), distance, setup, bx, by, bz, radius, null, 0f,
                presence, draw));
    }

    /**
     * Draw list with at most {@code max} screen passes: drops the latest-ordered, then farthest passes first. Particle
     * batches don't count (they are not full-screen passes; their per-frame slot cap is in {@code PostRenderer}), so
     * several particle effects at once don't push each other out.
     */
    List<Entry> drawList(int max) {
        kept.clear();
        kept.addAll(entries);
        int screen = 0;
        for (Entry e : entries) {
            if (!e.drawsGeometry()) {
                screen++;
            }
        }
        if (screen > max) {
            kept.sort(KEEP_ORDER);
            int[] seen = {0};
            kept.removeIf(e -> !e.drawsGeometry() && ++seen[0] > max);
        }
        kept.sort(DRAW_ORDER);
        if (lightCount > 0) {
            kept.add(0, lightsEntry()); // not counted against max: one pass for every effect's lights
        }
        return kept;
    }
}
