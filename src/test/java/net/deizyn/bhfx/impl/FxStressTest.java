package net.deizyn.bhfx.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /bhfx stress and /bhfx_play stress: how many start when, which ids, and where. */
class FxStressTest {
    private static final ResourceLocation A = new ResourceLocation("bhfx", "a");
    private static final ResourceLocation B = new ResourceLocation("bhfx", "b");
    private static final ResourceLocation C = new ResourceLocation("other", "c");

    @Test
    void noSpreadStartsEverythingInTheFirstTick() {
        FxStress s = new FxStress(List.of(A), Vec3.ZERO, 10, 70, 0, 1L);
        assertEquals(70, s.due());
        assertTrue(s.done());
        assertEquals(0, s.due());
    }

    @Test
    void spreadStartsEvenlyAndEndsOnTime() {
        FxStress s = new FxStress(List.of(A), Vec3.ZERO, 10, 70, 2, 1L); // 40 ticks
        int total = 0;
        int most = 0;
        for (int tick = 1; tick <= 40; tick++) {
            int n = s.due();
            total += n;
            most = Math.max(most, n);
            assertEquals(tick == 40, s.done(), "tick " + tick);
        }
        assertEquals(70, total);
        assertTrue(most <= 2, "at most 2 per tick, was " + most);
        assertEquals(0, s.due());
    }

    @Test
    void fewerStartsThanTicksStillAllStart() {
        FxStress s = new FxStress(List.of(A), Vec3.ZERO, 10, 3, 5, 1L);
        int total = 0;
        for (int tick = 0; tick < 100; tick++) {
            total += s.due();
        }
        assertEquals(3, total);
    }

    @Test
    void idsTakenInTurnInAStableOrder() {
        FxStress s = new FxStress(List.of(C, B, A), Vec3.ZERO, 0, 6, 0, 1L);
        assertEquals(A, s.id(0));
        assertEquals(B, s.id(1));
        assertEquals(C, s.id(2));
        assertEquals(A, s.id(3));
        assertEquals(3, s.kinds());
    }

    @Test
    void pointsStayOnTheDisc() {
        Vec3 center = new Vec3(100, 64, -40);
        FxStress s = new FxStress(List.of(A), center, 32, 10, 0, 7L);
        double far = 0;
        for (int i = 0; i < 2000; i++) {
            Vec3 p = s.nextPoint();
            assertEquals(64.0, p.y, 1e-9);
            double d = Math.hypot(p.x - center.x, p.z - center.z);
            assertTrue(d <= 32.0 + 1e-9, "outside the radius: " + d);
            far = Math.max(far, d);
        }
        assertTrue(far > 28.0, "points should reach the edge, farthest " + far);
    }

    @Test
    void sameSeedSamePoints() {
        FxStress a = new FxStress(List.of(A), Vec3.ZERO, 20, 5, 0, 42L);
        FxStress b = new FxStress(List.of(A), Vec3.ZERO, 20, 5, 0, 42L);
        for (int i = 0; i < 5; i++) {
            assertEquals(a.nextPoint(), b.nextPoint());
        }
    }

    @Test
    void countRadiusAndSecondsAreClamped() {
        FxStress s = new FxStress(List.of(A), Vec3.ZERO, 1e6, 10_000, 0, 1L);
        assertEquals(FxStress.MAX_COUNT, s.count());
        assertFalse(s.describe().contains("1000000"));
    }

    @Test
    void parseIdsAcceptsSpacesAndCommasAndDropsRepeats() {
        assertEquals(List.of(A, B, C), FxStress.parseIds(" bhfx:a, bhfx:b other:c bhfx:a "));
        assertEquals(List.of(new ResourceLocation("minecraft", "x")), FxStress.parseIds("x"));
        assertEquals(List.of(), FxStress.parseIds("  "));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> FxStress.parseIds("bhfx:a Bad:Id"));
        assertEquals("Bad:Id", e.getMessage());
    }

    @Test
    void noIdsIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> new FxStress(List.of(), Vec3.ZERO, 1, 1, 0, 1L));
    }
}
