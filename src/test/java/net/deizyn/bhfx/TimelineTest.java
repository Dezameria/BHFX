package net.deizyn.bhfx;

import net.deizyn.bhfx.api.FxSpawn;
import net.deizyn.bhfx.api.timeline.Ease;
import net.deizyn.bhfx.api.timeline.Timeline;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineTest {
    private static final float EPS = 1e-5f;
    private static final Timeline T = Timeline.ticks()
            .phase("warn", 0, 40)
            .event("impact", 40)
            .phase("beam", 40, 100)
            .build();

    @Test
    void phasesAndEvents() {
        assertTrue(T.in("warn", 0));
        assertTrue(T.in("warn", 39));
        assertFalse(T.in("warn", 40)); // end is exclusive
        assertTrue(T.at("impact", 40));
        assertTrue(T.in("impact", 40));
        assertFalse(T.in("impact", 41));
        assertEquals(40, T.start("beam"));
        assertEquals(100, T.end("beam"));
    }

    @Test
    void floatQueries() {
        assertEquals(0f, T.progress("beam", 10f), EPS);
        assertEquals(0.5f, T.progress("beam", 70f), EPS);
        assertEquals(1f, T.progress("beam", 500f), EPS);
        assertEquals(-1f, T.since("impact", 39.9f), EPS);
        assertEquals(1f, T.since("impact", 60f), EPS); // 20 ticks = 1 s
        assertEquals(0f, T.env("beam", 30f, 5, 5), EPS);
        assertEquals(1f, T.env("beam", 70f, 5, 5), EPS);
        assertEquals(0f, T.env("beam", 100f, 5, 5), EPS);
    }

    @Test
    void pulseAndDecay() {
        assertEquals(0f, T.pulse("impact", 39f, 2, 10), EPS);
        assertEquals(0.5f, T.pulse("impact", 41f, 2, 10), EPS);
        assertEquals(1f, T.pulse("impact", 42f, 2, 10), EPS);
        assertEquals(0.5f, T.pulse("impact", 47f, 2, 10), EPS);
        assertEquals(0f, T.pulse("impact", 80f, 2, 10), EPS);
        assertEquals(0f, T.decay("impact", 30f, 10), EPS);
        assertEquals(1f, T.decay("impact", 40f, 10), EPS);
        assertEquals(0.5f, T.decay("impact", 50f, 10), EPS);
    }

    @Test
    void unknownNamesAndBackwardPhasesFail() {
        assertThrows(IllegalArgumentException.class, () -> T.in("nope", 0));
        assertThrows(IllegalArgumentException.class, () -> Timeline.ticks().phase("bad", 10, 5));
    }

    @Test
    void ease() {
        assertEquals(0f, Ease.smooth(-1f), EPS);
        assertEquals(0.5f, Ease.smooth(0.5f), EPS);
        assertEquals(1f, Ease.smooth(2f), EPS);
        assertEquals(0.5f, Ease.smoother(0.5f), EPS);
        assertEquals(0f, Ease.window(0f, 1, 2, 8, 9), EPS);
        assertEquals(1f, Ease.window(5f, 1, 2, 8, 9), EPS);
        assertEquals(0f, Ease.window(9f, 1, 2, 8, 9), EPS);
        assertEquals(0f, Ease.expOut(-3f, 2f), EPS);
        assertTrue(Ease.expOut(5f, 2f) > 0.99f);
    }

    @Test
    void persistenceDefault() {
        FxSpawn spawn = FxSpawn.at(Vec3.ZERO);
        assertTrue(spawn.isPersistent()); // effect's own duration: assumed long
        assertFalse(FxSpawn.at(Vec3.ZERO).duration(100).isPersistent()); // 5 s or less
        assertTrue(FxSpawn.at(Vec3.ZERO).duration(101).isPersistent());
        assertTrue(FxSpawn.at(Vec3.ZERO).duration(FxSpawn.UNTIL_STOPPED).isPersistent());
        assertFalse(FxSpawn.at(Vec3.ZERO).duration(FxSpawn.UNTIL_STOPPED).persistent(false).isPersistent());
    }
}
