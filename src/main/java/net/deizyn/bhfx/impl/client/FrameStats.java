package net.deizyn.bhfx.impl.client;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The last 10 seconds of bhfx cost per frame and per tick, for /bhfx stats. Only frames and ticks with effects are
 * recorded (the others cost nothing), so an idle client keeps nothing here. Render thread only.
 */
final class FrameStats {
    private static final long WINDOW_NANOS = 10_000_000_000L;

    /** One rendered frame. gpuMs < 0 when no GPU measurement finished that frame. */
    private record Frame(long at, long cpuNanos, double gpuMs, int fx, int passes, int slots, int stride) {
    }

    private record Tick(long at, long cpuNanos) {
    }

    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Deque<Tick> ticks = new ArrayDeque<>();

    void frame(long now, long cpuNanos, double gpuMs, int fx, int passes, int slots, int stride) {
        frames.addLast(new Frame(now, cpuNanos, gpuMs, fx, passes, slots, stride));
        while (!frames.isEmpty() && now - frames.peekFirst().at() > WINDOW_NANOS) {
            frames.removeFirst();
        }
    }

    void tick(long now, long cpuNanos) {
        ticks.addLast(new Tick(now, cpuNanos));
        while (!ticks.isEmpty() && now - ticks.peekFirst().at() > WINDOW_NANOS) {
            ticks.removeFirst();
        }
    }

    void clear() {
        frames.clear();
        ticks.clear();
    }

    /** Report lines; frames without effects cost nothing and are only counted. */
    String[] report(long now) {
        return report(now, WINDOW_NANOS);
    }

    /** The same over the last {@code window} nanoseconds (at most the 10 s kept). */
    String[] report(long now, long window) {
        window = Math.max(1L, Math.min(window, WINDOW_NANOS));
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        long gapMax = 0L;
        long slotSum = 0L;
        int slotMax = 0;
        int strideMax = 1;
        int busy = 0;
        long cpuSum = 0L;
        long cpuMax = 0L;
        int gpuCount = 0;
        double gpuSum = 0.0;
        double gpuMax = 0.0;
        int fxMax = 0;
        int passMax = 0;
        for (Frame f : frames) {
            if (now - f.at() > window) {
                continue;
            }
            if (last != Long.MIN_VALUE) {
                gapMax = Math.max(gapMax, f.at() - last);
            }
            first = Math.min(first, f.at());
            last = f.at();
            slotSum += f.slots();
            slotMax = Math.max(slotMax, f.slots());
            strideMax = Math.max(strideMax, f.stride());
            busy++;
            cpuSum += f.cpuNanos();
            cpuMax = Math.max(cpuMax, f.cpuNanos());
            if (f.gpuMs() >= 0) {
                gpuCount++;
                gpuSum += f.gpuMs();
                gpuMax = Math.max(gpuMax, f.gpuMs());
            }
            fxMax = Math.max(fxMax, f.fx());
            passMax = Math.max(passMax, f.passes());
        }
        int tickCount = 0;
        long tickSum = 0L;
        long tickMax = 0L;
        for (Tick t : ticks) {
            if (now - t.at() <= window) {
                tickCount++;
                tickSum += t.cpuNanos();
                tickMax = Math.max(tickMax, t.cpuNanos());
            }
        }
        if (busy == 0) {
            return new String[]{String.format("last %.0f s: no frames with effects (bhfx did no work)", window / 1e9)};
        }
        return new String[]{
                String.format("last %.0f s: %d frames with effects (up to %d fx, %d passes)", window / 1e9, busy, fxMax, passMax),
                busy > 1 && last > first
                        ? String.format("  frames: %.1f per second while effects ran, longest frame %.1f ms",
                        (busy - 1) * 1e9 / (last - first), gapMax / 1e6)
                        : "  frames: too few to rate",
                String.format("  particles: avg %d slot(s) drawn, max %d, thinning up to 1 in %d", slotSum / busy, slotMax, strideMax),
                String.format("  render CPU: avg %.3f ms, max %.3f ms per frame", cpuSum / 1e6 / busy, cpuMax / 1e6),
                gpuCount > 0
                        ? String.format("  GPU: avg %.2f ms, max %.2f ms (%d measured frames)", gpuSum / gpuCount, gpuMax, gpuCount)
                        : "  GPU: not measured (no timer queries on this driver, or no passes drawn)",
                String.format("  tick CPU: avg %.3f ms, max %.3f ms (%d ticks with effects)", tickCount > 0 ? tickSum / 1e6 / tickCount : 0.0,
                        tickMax / 1e6, tickCount)
        };
    }
}
