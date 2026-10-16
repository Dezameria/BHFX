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
    private record Frame(long at, long cpuNanos, double gpuMs, int fx, int passes) {
    }

    private record Tick(long at, long cpuNanos) {
    }

    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Deque<Tick> ticks = new ArrayDeque<>();

    void frame(long now, long cpuNanos, double gpuMs, int fx, int passes) {
        frames.addLast(new Frame(now, cpuNanos, gpuMs, fx, passes));
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
        int busy = 0;
        long cpuSum = 0L;
        long cpuMax = 0L;
        int gpuCount = 0;
        double gpuSum = 0.0;
        double gpuMax = 0.0;
        int fxMax = 0;
        int passMax = 0;
        for (Frame f : frames) {
            if (now - f.at() > WINDOW_NANOS) {
                continue;
            }
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
            if (now - t.at() <= WINDOW_NANOS) {
                tickCount++;
                tickSum += t.cpuNanos();
                tickMax = Math.max(tickMax, t.cpuNanos());
            }
        }
        if (busy == 0) {
            return new String[]{"last 10 s: no frames with effects (bhfx did no work)"};
        }
        return new String[]{
                String.format("last 10 s: %d frames with effects (up to %d fx, %d passes)", busy, fxMax, passMax),
                String.format("  render CPU: avg %.3f ms, max %.3f ms per frame", cpuSum / 1e6 / busy, cpuMax / 1e6),
                gpuCount > 0
                        ? String.format("  GPU: avg %.2f ms, max %.2f ms (%d measured frames)", gpuSum / gpuCount, gpuMax, gpuCount)
                        : "  GPU: not measured (no timer queries on this driver, or no passes drawn)",
                String.format("  tick CPU: avg %.3f ms, max %.3f ms (%d ticks with effects)", tickCount > 0 ? tickSum / 1e6 / tickCount : 0.0,
                        tickMax / 1e6, tickCount)
        };
    }
}
