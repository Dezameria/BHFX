package net.deizyn.bhfx.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * One run of /bhfx stress or /bhfx_play stress: {@code count} effects round a point, taken in turn from a list of
 * effect ids, all at once or spread evenly over some seconds. Shared by the client and the server command; holds no
 * game state, so it can be unit tested.
 */
public final class FxStress {
    public static final int MAX_COUNT = 256;
    public static final double MAX_RADIUS = 128.0;
    public static final int MAX_SECONDS = 60;

    private final List<ResourceLocation> ids;
    private final Vec3 center;
    private final double radius;
    private final int count;
    private final int ticks;
    private final RandomSource random;
    private int started;
    private int age;
    private int refused;

    public FxStress(Collection<ResourceLocation> ids, Vec3 center, double radius, int count, int seconds, long seed) {
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("no effect ids");
        }
        List<ResourceLocation> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.comparing(ResourceLocation::toString));
        this.ids = List.copyOf(sorted);
        this.center = center;
        this.radius = Math.max(0.0, Math.min(MAX_RADIUS, radius));
        this.count = Math.max(1, Math.min(MAX_COUNT, count));
        this.ticks = Math.max(0, Math.min(MAX_SECONDS, seconds)) * 20;
        this.random = RandomSource.create(seed);
    }

    /**
     * Effect ids typed after the numbers, separated by spaces or commas ({@code "a:x b:y"}). Throws with the bad
     * word when one is not an id.
     */
    public static List<ResourceLocation> parseIds(String text) {
        List<ResourceLocation> out = new ArrayList<>();
        for (String word : text.trim().split("[\\s,]+")) {
            if (word.isEmpty()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(word);
            if (id == null) {
                throw new IllegalArgumentException(word);
            }
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** How many to start now; call once per tick (the first call starts everything when there is no spread). */
    public int due() {
        age++;
        int target = ticks <= 0 ? count : (int) Math.min(count, (long) count * age / ticks);
        int n = Math.max(0, target - started);
        started += n;
        return n;
    }

    /** The effect id for the {@code index}-th start: the ids in turn, so a mix of effects plays at once. */
    public ResourceLocation id(int index) {
        return ids.get(Math.floorMod(index, ids.size()));
    }

    /** A random point on the flat disc of the radius round the center, spread evenly by area. */
    public Vec3 nextPoint() {
        double r = radius * Math.sqrt(random.nextDouble());
        double a = random.nextDouble() * Math.PI * 2.0;
        return center.add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
    }

    public void refused() {
        refused++;
    }

    public int refusedCount() {
        return refused;
    }

    /** Starts handed out so far (some may have been refused). */
    public int startedCount() {
        return started;
    }

    public int count() {
        return count;
    }

    public boolean done() {
        return started >= count;
    }

    public int kinds() {
        return ids.size();
    }

    public String describe() {
        return count + " effect(s) of " + ids.size() + " kind(s) within " + Math.round(radius) + " blocks"
                + (ticks > 0 ? " over " + ticks / 20 + " s" : " at once");
    }
}
