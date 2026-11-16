package net.deizyn.bhfx.impl.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WavesTest {
    @Test
    void delayIsDistanceOverSpeed() {
        Vec3 listener = new Vec3(0, 64, 0);
        assertEquals(1.0, Waves.delaySeconds(listener, new Vec3(343, 64, 0), Waves.SPEED_OF_SOUND), 1e-9);
        assertEquals(0.0, Waves.delaySeconds(listener, new Vec3(100, 64, 0), 0f), 1e-9); // instant
        assertEquals(0.0, Waves.delaySeconds(listener, listener, Waves.SPEED_OF_SOUND), 1e-9);
    }

    @Test
    void delayRoundsToTicks() {
        assertEquals(20, Waves.delayTicks(1.0));
        assertEquals(9, Waves.delayTicks(150.0 / 343.0)); // 0.437 s
        assertEquals(0, Waves.delayTicks(0.02));
        assertEquals(0, Waves.delayTicks(-1.0));
    }

    @Test
    void scheduleFiresInDueOrderThenAddOrder() {
        Waves.Schedule<String> s = new Waves.Schedule<>(8);
        s.add(5, "b");
        s.add(3, "a");
        s.add(5, "c");
        assertNull(s.poll(2));
        assertEquals("a", s.poll(3));
        assertNull(s.poll(4));
        assertEquals("b", s.poll(10));
        assertEquals("c", s.poll(10));
        assertNull(s.poll(10));
    }

    @Test
    void scheduleIsBounded() {
        Waves.Schedule<Integer> s = new Waves.Schedule<>(2);
        assertTrue(s.add(1, 1));
        assertTrue(s.add(1, 2));
        assertFalse(s.add(1, 3));
        assertEquals(2, s.size());
        s.clear();
        assertEquals(0, s.size());
    }
}
