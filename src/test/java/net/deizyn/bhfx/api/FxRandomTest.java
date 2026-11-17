package net.deizyn.bhfx.api;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxRandomTest {
    private static final long STRIKES = FxRandom.stream("strikes");

    @Test
    void sameKeyGivesTheSameNumbersWhateverWasDrawnBefore() {
        RandomSource a = FxRandom.source(42L, STRIKES, 7);
        // another client drew from other keys first (it was built earlier): key 7 must not care
        for (int i = 0; i < 7; i++) {
            FxRandom.source(42L, STRIKES, i).nextDouble();
        }
        RandomSource b = FxRandom.source(42L, STRIKES, 7);
        for (int i = 0; i < 16; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
        assertEquals(FxRandom.unit(42L, STRIKES, 7), FxRandom.unit(42L, STRIKES, 7));
    }

    @Test
    void seedStreamAndIndexEachChangeTheResult() {
        long base = FxRandom.seed(42L, STRIKES, 7);
        assertNotEquals(base, FxRandom.seed(43L, STRIKES, 7));
        assertNotEquals(base, FxRandom.seed(42L, FxRandom.stream("embers"), 7));
        assertNotEquals(base, FxRandom.seed(42L, STRIKES, 8));
        // stream and index are not interchangeable
        assertNotEquals(FxRandom.seed(42L, 1, 2), FxRandom.seed(42L, 2, 1));
        assertNotEquals(FxRandom.stream("ab"), FxRandom.stream("ba"));
    }

    @Test
    void neighbouringIndexesSpreadOverTheWholeRange() {
        int n = 4096;
        int[] buckets = new int[16];
        Set<Long> seen = new HashSet<>();
        double sum = 0;
        for (int i = 0; i < n; i++) {
            float u = FxRandom.unit(0L, 0L, i);
            assertTrue(u >= 0f && u < 1f, "unit " + u);
            buckets[(int) (u * 16)]++;
            sum += u;
            seen.add(FxRandom.seed(0L, 0L, i));
        }
        assertEquals(n, seen.size());
        assertEquals(0.5, sum / n, 0.03);
        for (int count : buckets) {
            assertTrue(count > n / 16 / 2 && count < n / 16 * 2, "bucket " + count);
        }
        float r = FxRandom.range(1L, 2L, 3L, -2f, 6f);
        assertTrue(r >= -2f && r < 6f);
    }
}
