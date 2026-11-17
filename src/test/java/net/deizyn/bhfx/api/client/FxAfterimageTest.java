package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FxAfterimageTest {
    @Test
    void keepsAGhostPerIntervalOnlyWhileMoving() {
        FxAfterimage trail = new FxAfterimage(4, 0.1f);
        assertEquals(0.4f, trail.span(), 1e-6);
        trail.record(0f, FxFrame.at(new Vec3(0, 0, 0)), 1f, null);
        trail.record(0.05f, FxFrame.at(new Vec3(1, 0, 0)), 1f, null); // too soon
        assertEquals(1, trail.count());
        trail.record(0.1f, FxFrame.at(new Vec3(2, 0, 0)), 1f, null);
        trail.record(0.25f, FxFrame.at(new Vec3(2, 0, 0)), 1f, null); // has not moved
        assertEquals(2, trail.count());
        for (int i = 0; i < 10; i++) {
            trail.record(0.3f + i * 0.1f, FxFrame.at(new Vec3(3 + i, 0, 0)), 1f, null);
        }
        assertEquals(4, trail.count()); // never more than copies
        trail.record(0.2f, FxFrame.at(new Vec3(50, 0, 0)), 1f, null); // the clock went back: start again
        assertEquals(1, trail.count());
        trail.clear();
        assertEquals(0, trail.count());
    }

    @Test
    void poseSetCopies() {
        FxPose a = new FxPose().rotation("blade", 1, 2, 3).scale("gem", 0.5f);
        FxPose b = new FxPose().position("old", 9, 9, 9);
        int before = b.version();
        b.set(a);
        assertNull(b.channels("old"));
        assertArrayEquals(a.channels("blade"), b.channels("blade"));
        assertArrayEquals(a.channels("gem"), b.channels("gem"));
        assertEquals(before + 1, b.version());
        a.rotation("blade", 7, 7, 7);
        assertEquals(1f, b.channels("blade")[0]); // a copy, not the same arrays
    }
}
