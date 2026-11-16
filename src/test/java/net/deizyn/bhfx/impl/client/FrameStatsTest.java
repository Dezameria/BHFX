package net.deizyn.bhfx.impl.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameStatsTest {
    private static final long MS = 1_000_000L;

    @Test
    void windowRatesOnlyItsOwnFrames() {
        FrameStats s = new FrameStats();
        long t = 0;
        // 5 s at 10 ms frames with 100 slots, then 2 s at 5 ms frames with 300 slots and one 40 ms frame
        for (int i = 0; i < 500; i++) {
            t += 10 * MS;
            s.frame(t, MS, 1.0, 1, 1, 100, 1);
        }
        for (int i = 0; i < 400; i++) {
            t += (i == 200 ? 40 : 5) * MS;
            s.frame(t, 2 * MS, 2.0, 2, 3, 300, 4);
        }
        String all = String.join("\n", s.report(t));
        assertTrue(all.contains("thinning up to 1 in 4"), all);
        String recent = String.join("\n", s.report(t, 1_000 * MS));
        assertTrue(recent.contains("last 1 s"), recent);
        assertTrue(recent.contains("200.0 per second"), recent);
        assertTrue(recent.contains("avg 300 slot(s) drawn, max 300"), recent);
        assertTrue(recent.contains("render CPU: avg 2.000 ms"), recent);
        String mid = String.join("\n", s.report(t, 1_500 * MS));
        assertTrue(mid.contains("longest frame 40.0 ms"), mid);
    }

    @Test
    void emptyWindowSaysSo() {
        FrameStats s = new FrameStats();
        assertTrue(s.report(5_000 * MS, 2_000 * MS)[0].contains("last 2 s: no frames"));
    }
}
