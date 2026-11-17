package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxShaftsTest {
    @Test
    void narrowConeGetsASmallBallOnItsAxis() {
        PassOptions.Bounds b = FxShafts.cone(new Vec3(0, 10, 0), new Vec3(0, -3, 0), 10f, 20f).bounds();
        assertEquals(5.0, b.center().y, 1e-6);
        assertTrue(b.radius() < 6.0, "radius " + b.radius());
        // the apex and a point on the rim of the cone's far end are inside
        assertTrue(b.center().distanceTo(new Vec3(0, 10, 0)) <= b.radius());
        double a = Math.toRadians(20);
        Vec3 rim = new Vec3(Math.sin(a) * 10, 10 - Math.cos(a) * 10, 0);
        assertTrue(b.center().distanceTo(rim) <= b.radius());
    }

    @Test
    void wideConeAndBallUseTheSourceAsCentre() {
        PassOptions.Bounds wide = FxShafts.cone(new Vec3(1, 2, 3), new Vec3(1, 0, 0), 8f, 80f).bounds();
        assertEquals(new Vec3(1, 2, 3), wide.center());
        assertEquals(8.25, wide.radius(), 1e-6);
        PassOptions.Bounds ball = FxShafts.around(new Vec3(1, 2, 3), 4f).bounds();
        assertEquals(new Vec3(1, 2, 3), ball.center());
        assertEquals(4.25, ball.radius(), 1e-6);
    }
}
