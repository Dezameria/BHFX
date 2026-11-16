package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PassOptionsTest {
    @Test
    void halfResAndBoundsKeepEachOther() {
        assertEquals(0f, PassOptions.DEFAULT.layer());
        assertEquals(0f, new PassOptions(10, null).layer());
        PassOptions a = new PassOptions(7).halfRes().within(new Vec3(1, 2, 3), 4.0);
        assertEquals(7, a.order());
        assertEquals(0.5f, a.layer());
        assertNotNull(a.bounds());
        PassOptions b = new PassOptions(7).within(new Vec3(1, 2, 3), 4.0).halfRes();
        assertEquals(a, b);
        assertNull(PassOptions.OVERLAY.halfRes().bounds());
    }
}
