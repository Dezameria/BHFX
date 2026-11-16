package net.deizyn.bhfx.impl.render;

import net.deizyn.bhfx.api.Follow;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxInit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The live window: only slots from the oldest not-yet-dead one to the newest are drawn. */
class ParticleWindowTest {
    private static final ResourceLocation ID = new ResourceLocation("bhfx", "test");

    private static Fx fx() {
        return new Fx(new FxInit(ID, -1, Vec3.ZERO, Vec3.ZERO, -1, -1, Follow.NONE, 1L, 100, 0, FxParams.EMPTY)) {
        };
    }

    @AfterEach
    void resetClock() {
        ParticleBatch.clockOffset(1f);
    }

    /** Spawn at batch time {@code t} (seconds) with that life and delay. */
    private static void spawnAt(ParticleBatch b, float t, float life, float delay) {
        ParticleBatch.clockOffset(t * 20f);
        b.spawn(0, 0, 0).life(life).delay(delay);
    }

    @Test
    void deadSlotsLeaveTheOldEnd() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 16);
        for (int i = 0; i < 10; i++) {
            spawnAt(b, i * 0.1f, 0.5f, 0f); // births 0 .. 0.9 s, each dies 0.5 s later
        }
        assertEquals(10, b.live);
        b.advanceWindow(0.5f); // nothing dead past the margin yet
        assertEquals(10, b.live);
        b.advanceWindow(1.0f + ParticleBatch.WINDOW_MARGIN + 0.01f); // births up to 0.5 s died by 1.0 s
        assertEquals(4, b.live);
        assertEquals(6, b.liveStart());
        b.advanceWindow(10f);
        assertEquals(0, b.live);
        assertEquals(10, b.filled, "filled is untouched (alive() still walks every slot)");
    }

    @Test
    void aLongOrDelayedParticleHoldsTheWindowOpen() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 16);
        spawnAt(b, 0f, 0.2f, 0f);
        spawnAt(b, 0f, 0.2f, 2f); // born at 2 s: must stay drawable until 2.2 s
        spawnAt(b, 0f, 0.2f, 0f);
        b.advanceWindow(1f);
        assertEquals(2, b.live, "only the first (dead) slot goes; the delayed one stops the window there");
        b.advanceWindow(3f);
        assertEquals(0, b.live);
    }

    @Test
    void wrapsWithTheRing() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 4);
        for (int i = 0; i < 6; i++) {
            spawnAt(b, i, 0.5f, 0f); // slots 0 1 2 3 0 1
        }
        assertEquals(4, b.live);
        assertEquals(2, b.liveStart());
        b.advanceWindow(5f); // births 2, 3, 4 dead by 5 s (+ margin); birth 5 alive
        assertEquals(1, b.live);
        assertEquals(1, b.liveStart());
        b.clear();
        assertEquals(0, b.live);
    }

    @Test
    void runsCoverTheWindowWithTheSameSlotsEveryFrame() {
        int[] out = new int[3];
        ParticleBatch.windowRuns(0, 10, 16, 1, out);
        assertArrayEquals(new int[]{0, 10, 0}, out, "no wrap: as before the window");
        ParticleBatch.windowRuns(5, 6, 16, 1, out);
        assertArrayEquals(new int[]{5, 6, 0}, out);
        ParticleBatch.windowRuns(14, 5, 16, 1, out);
        assertArrayEquals(new int[]{14, 2, 3}, out, "slots 14 15 | 0 1 2");
        // thinned: only multiples of the stride, whatever the start
        for (int start = 0; start < 16; start++) {
            for (int live = 0; live <= 16; live++) {
                ParticleBatch.windowRuns(start, live, 16, 3, out);
                Set<Integer> drawn = new HashSet<>();
                for (int i = 0; i < out[1]; i++) {
                    drawn.add(out[0] + i * 3);
                }
                for (int i = 0; i < out[2]; i++) {
                    drawn.add(i * 3);
                }
                Set<Integer> expected = new HashSet<>();
                for (int k = 0; k < live; k++) {
                    int slot = (start + k) % 16;
                    if (slot % 3 == 0) {
                        expected.add(slot);
                    }
                }
                assertEquals(expected, drawn, "start " + start + " live " + live);
                for (int s : drawn) {
                    assertTrue(s >= 0 && s < 16);
                }
            }
        }
    }
}
