package net.deizyn.bhfx.impl.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;
import net.deizyn.bhfx.api.client.Uniforms;
import net.deizyn.bhfx.impl.render.ParticleBatch;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Passes collected from every effect in one frame. */
public final class PassListImpl implements PassList {
    /**
     * {@code bx, by, bz} = camera-relative centre of the bounds; {@code radius <= 0} = whole screen (for particles: no
     * bounds given, the batch's own sphere is used). {@code particles} is set for a particle batch, drawn at {@code time}.
     */
    public record Entry(ResourceLocation shader, int order, int seq, double distance, Consumer<Uniforms> setup,
                        double bx, double by, double bz, double radius, @Nullable ParticleBatch particles, float time,
                        float presence) {
    }

    private static final Comparator<Entry> DRAW_ORDER = Comparator.comparingInt(Entry::order).thenComparingInt(Entry::seq);
    private static final Comparator<Entry> KEEP_ORDER =
            Comparator.comparingInt(Entry::order).thenComparingDouble(Entry::distance).thenComparingInt(Entry::seq);

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> kept = new ArrayList<>();
    private double distance;
    private float presence = 1f;
    private Vec3 camera = Vec3.ZERO;

    /** Start a frame; bounds are stored relative to {@code camera}. */
    void clear(Vec3 camera) {
        entries.clear();
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
        return entries.isEmpty();
    }

    @Override
    public void add(ResourceLocation shader, Consumer<Uniforms> setup) {
        add(shader, PassOptions.DEFAULT, setup);
    }

    @Override
    public void add(ResourceLocation shader, PassOptions options, Consumer<Uniforms> setup) {
        PassOptions.Bounds b = options.bounds();
        if (b == null) {
            entries.add(new Entry(shader, options.order(), entries.size(), distance, setup, 0, 0, 0, 0, null, 0f, presence));
        } else {
            Vec3 c = b.center();
            entries.add(new Entry(shader, options.order(), entries.size(), distance, setup,
                    c.x - camera.x, c.y - camera.y, c.z - camera.z, b.radius(), null, 0f, presence));
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
                batch, now, presence));
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
            if (e.particles() == null) {
                screen++;
            }
        }
        if (screen > max) {
            kept.sort(KEEP_ORDER);
            int[] seen = {0};
            kept.removeIf(e -> e.particles() == null && ++seen[0] > max);
        }
        kept.sort(DRAW_ORDER);
        return kept;
    }
}
