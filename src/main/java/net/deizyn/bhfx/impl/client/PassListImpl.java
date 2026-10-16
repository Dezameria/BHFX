package net.deizyn.bhfx.impl.client;

import net.minecraft.resources.ResourceLocation;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;
import net.deizyn.bhfx.api.client.Uniforms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Passes collected from every effect in one frame. */
public final class PassListImpl implements PassList {
    public record Entry(ResourceLocation shader, int order, int seq, double distance, Consumer<Uniforms> setup) {
    }

    private static final Comparator<Entry> DRAW_ORDER = Comparator.comparingInt(Entry::order).thenComparingInt(Entry::seq);
    private static final Comparator<Entry> KEEP_ORDER =
            Comparator.comparingInt(Entry::order).thenComparingDouble(Entry::distance).thenComparingInt(Entry::seq);

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> kept = new ArrayList<>();
    private double distance;

    void clear() {
        entries.clear();
    }

    void currentDistance(double distance) {
        this.distance = distance;
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
        entries.add(new Entry(shader, options.order(), entries.size(), distance, setup));
    }

    /** Draw list capped at {@code max}: drops the latest-ordered, then farthest passes first. */
    List<Entry> drawList(int max) {
        kept.clear();
        kept.addAll(entries);
        if (kept.size() > max) {
            kept.sort(KEEP_ORDER);
            kept.subList(max, kept.size()).clear();
        }
        kept.sort(DRAW_ORDER);
        return kept;
    }
}
