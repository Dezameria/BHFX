package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.api.client.PassList;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassListLightsTest {
    @Test
    void lightsMakeOnePassDrawnFirstAndNotCounted() {
        PassListImpl list = new PassListImpl();
        list.clear(new Vec3(100, 64, 100));
        assertTrue(list.isEmpty());
        list.light(new Vec3(104, 64, 100), 0xFF0000, 1f, 6f);
        list.light(new Vec3(100, 64, 110), 0x00FF00, 1f, 4f);
        assertFalse(list.isEmpty());
        List<PassListImpl.Entry> draw = list.drawList(0);
        assertEquals(1, draw.size());
        PassListImpl.Entry e = draw.get(0);
        assertEquals("bhfx:lights", e.shader().toString());
        // bounds hold both spheres: x -4..10, z -6..14 round the camera
        assertEquals(3.0, e.bx(), 1e-4);
        assertEquals(4.0, e.bz(), 1e-4);
        assertTrue(e.radius() >= 10.0);
        list.clear(new Vec3(0, 0, 0));
        assertTrue(list.isEmpty());
        assertEquals(0, list.drawList(8).size());
    }

    @Test
    void weakOrEmptyLightsAreIgnoredAndTheNearestAreKept() {
        PassListImpl list = new PassListImpl();
        list.clear(Vec3.ZERO);
        list.light(new Vec3(1, 0, 0), 0xFFFFFF, 0f, 5f);
        list.light(new Vec3(1, 0, 0), 0xFFFFFF, 1f, 0f);
        assertEquals(0, list.lightCount());
        for (int i = 0; i < PassList.MAX_LIGHTS + 20; i++) {
            list.light(new Vec3(1000 - i * 10, 0, 0), 0xFFFFFF, 1f, 2f); // each new one is nearer
        }
        assertEquals(PassList.MAX_LIGHTS, list.lightCount());
        // the 20 farthest were replaced: the bounds no longer reach x = 1000
        PassListImpl.Entry e = list.drawList(8).get(0);
        assertTrue(e.bx() + e.radius() < 1000 - 19 * 10, "far lights should be gone: " + (e.bx() + e.radius()));
    }
}
