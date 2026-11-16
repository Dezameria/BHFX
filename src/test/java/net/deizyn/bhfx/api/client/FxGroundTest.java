package net.deizyn.bhfx.api.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxGroundTest {
    /** Ground at y = x (so each point's answer is easy to check); every lookup "takes" 1 ms on the fake clock. */
    private static FxGround.Batch batch(int capacity, AtomicLong clock) {
        return new FxGround.Batch(capacity, 8, (x, y, z, search) -> {
            clock.addAndGet(1_000_000L);
            return x < 0 ? Double.NaN : x;
        }, clock::get);
    }

    @Test
    void resolvesInOrderWithinTheBudget() {
        AtomicLong clock = new AtomicLong();
        FxGround.Batch b = batch(10, clock);
        for (int i = 0; i < 10; i++) {
            assertEquals(i, b.add(i, 64, 0));
        }
        assertEquals(3, b.update(2.5)); // 1 ms each: the third starts at 2 ms, still inside 2.5
        assertEquals(3, b.resolvedCount());
        assertTrue(b.resolved(2));
        assertFalse(b.resolved(3));
        assertEquals(2.0, b.surface(2));
        assertTrue(Double.isNaN(b.surface(5)));
        assertEquals(64.0, b.surfaceOr(5, 64.0));
    }

    @Test
    void alwaysMakesProgressAndFinishes() {
        AtomicLong clock = new AtomicLong();
        FxGround.Batch b = batch(4, clock);
        for (int i = 0; i < 4; i++) {
            b.add(i, 0, 0);
        }
        assertEquals(1, b.update(0)); // no budget still does one
        b.finish();
        assertTrue(b.done());
        assertEquals(0, b.update(5));
        assertEquals(3.0, b.surface(3));
    }

    @Test
    void noGroundStaysNaNAndFullBatchRefuses() {
        AtomicLong clock = new AtomicLong();
        FxGround.Batch b = batch(1, clock);
        assertEquals(0, b.add(-5, 0, 0));
        assertEquals(-1, b.add(1, 0, 0));
        b.finish();
        assertTrue(Double.isNaN(b.surface(0)));
        assertEquals(7.0, b.surfaceOr(0, 7.0));
        assertEquals(1, b.size());
    }
}
