package net.deizyn.bhfx.impl.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolutionControllerTest {
    private static final long S = 1_000_000_000L;

    @Test
    void dropsAtOnceAndRisesOneStepPerHold() {
        ResolutionController r = new ResolutionController();
        long t = 10 * S;
        assertEquals(1.0, r.share(t));
        r.sample(16.0, 4.0); // 4x over budget: wants a quarter of the pixels
        assertEquals(0.25, r.share(t), 1e-9);

        for (int i = 0; i < 200; i++) {
            r.sample(0.5, 4.0); // far under budget: wants full resolution again
        }
        assertEquals(0.25, r.share(t + S), 1e-9, "held for 2 s after the drop");
        assertEquals(0.375, r.share(t + 2 * S), 1e-9, "then one step");
        assertEquals(0.375, r.share(t + 3 * S), 1e-9);
        assertEquals(0.5, r.share(t + 4 * S), 1e-9);
        assertEquals(0.5, r.currentShare(), 1e-9);
    }

    @Test
    void resetGoesBackToFull() {
        ResolutionController r = new ResolutionController();
        r.sample(16.0, 4.0);
        r.share(5 * S);
        r.reset();
        assertEquals(1.0, r.share(5 * S));
    }
}
