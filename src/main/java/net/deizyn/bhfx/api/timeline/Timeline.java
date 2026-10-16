package net.offkung.bhfx.api.timeline;

import java.util.HashMap;
import java.util.Map;

/**
 * Named phases and events in ticks, declared once as a constant:
 * <pre>{@code
 * static final Timeline T = Timeline.ticks()
 *         .phase("warn", 0, 30).event("impact", 30).phase("burn", 30, 260).build();
 * }</pre>
 * Integer queries ({@link #at}, {@link #in}) are for {@code tick()}; float queries take {@code ticks + partialTick}
 * and are for {@code passes()} / {@code screen()}.
 */
public final class Timeline {
    private record Span(int start, int end) {
    }

    private final Map<String, Span> spans;

    private Timeline(Map<String, Span> spans) {
        this.spans = spans;
    }

    public static Builder ticks() {
        return new Builder();
    }

    private Span span(String name) {
        Span span = spans.get(name);
        if (span == null) {
            throw new IllegalArgumentException("No phase or event '" + name + "' in timeline " + spans.keySet());
        }
        return span;
    }

    public int start(String name) {
        return span(name).start;
    }

    public int end(String name) {
        return span(name).end;
    }

    /** True exactly on the tick the event happens / the phase begins. */
    public boolean at(String name, int tick) {
        return tick == span(name).start;
    }

    /** True while inside the phase [start, end); for an event, only on its tick. */
    public boolean in(String name, int tick) {
        Span s = span(name);
        return s.end == s.start ? tick == s.start : tick >= s.start && tick < s.end;
    }

    /** 0..1 through the phase. */
    public float progress(String name, float t) {
        Span s = span(name);
        return Ease.clamp01((t - s.start) / Math.max(s.end - s.start, 1));
    }

    /** Seconds since the event / phase start, or -1 before it. */
    public float since(String name, float t) {
        int start = span(name).start;
        return t < start ? -1f : (t - start) / 20f;
    }

    /** Smooth 0→1→0 over the phase with the given fade lengths in ticks. */
    public float env(String name, float t, float fadeInTicks, float fadeOutTicks) {
        Span s = span(name);
        return Ease.window(t, s.start, s.start + fadeInTicks, s.end - fadeOutTicks, s.end);
    }

    /** A short spike at the event: rises linearly over riseTicks, falls linearly over fallTicks. */
    public float pulse(String name, float t, float riseTicks, float fallTicks) {
        float x = t - span(name).start;
        if (x < 0f) {
            return 0f;
        }
        if (x < riseTicks) {
            return x / riseTicks;
        }
        if (fallTicks <= 0f) {
            return 0f;
        }
        return Math.max(0f, 1f - (x - riseTicks) / fallTicks);
    }

    /** 1 at the start, halving every halfLifeTicks; 0 before the start. */
    public float decay(String name, float t, float halfLifeTicks) {
        float x = t - span(name).start;
        if (x < 0f) {
            return 0f;
        }
        return (float) Math.pow(0.5, x / Math.max(halfLifeTicks, 1e-3f));
    }

    public static final class Builder {
        private final Map<String, Span> spans = new HashMap<>();

        private Builder() {
        }

        public Builder phase(String name, int start, int end) {
            if (end < start) {
                throw new IllegalArgumentException("Phase '" + name + "' ends before it starts");
            }
            spans.put(name, new Span(start, end));
            return this;
        }

        public Builder event(String name, int tick) {
            spans.put(name, new Span(tick, tick));
            return this;
        }

        public Timeline build() {
            return new Timeline(Map.copyOf(spans));
        }
    }
}
