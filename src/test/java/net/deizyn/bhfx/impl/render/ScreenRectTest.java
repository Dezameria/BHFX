package net.deizyn.bhfx.impl.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Screen area of a bounded pass: must contain the whole sphere, and fall back to FULL whenever that is unsure. */
class ScreenRectTest {
    private static final float EPS = 1e-4f;
    // Minecraft's camera looks down -z; 90 degree vertical fov, square screen
    private static final Matrix4f VIEW_PROJ = new Matrix4f().perspective((float) Math.toRadians(90), 1f, 0.05f, 1000f);
    private final Vector4f scratch = new Vector4f();

    private ScreenRect of(double x, double y, double z, double r) {
        return ScreenRect.ofSphere(VIEW_PROJ, x, y, z, r, scratch);
    }

    @Test
    void smallSphereAheadIsAroundTheCentre() {
        ScreenRect r = of(0, 0, -20, 1);
        assertFalse(r.empty());
        assertFalse(r.full());
        assertEquals(0.5f, (r.minU() + r.maxU()) / 2, EPS);
        assertEquals(0.5f, (r.minV() + r.maxV()) / 2, EPS);
        // the near face of the cube (z = -19) sets the size: 1/19 in ndc = 1/38 in uv
        assertEquals(0.5f - 1f / 38f, r.minU(), EPS);
        assertEquals(0.5f + 1f / 38f, r.maxU(), EPS);
    }

    @Test
    void rectContainsTheSphereSilhouette() {
        // sphere off to the right and up; its silhouette edge points must all fall inside the rect
        double cx = 6, cy = 3, cz = -15, rad = 2;
        ScreenRect r = of(cx, cy, cz, rad);
        for (int i = 0; i < 360; i += 5) {
            double a = Math.toRadians(i);
            for (int k = 0; k < 3; k++) {
                // points on three great circles of the sphere
                double px = cx + rad * (k == 2 ? 0 : Math.cos(a));
                double py = cy + rad * (k == 0 ? Math.sin(a) : k == 2 ? Math.cos(a) : 0);
                double pz = cz + rad * (k == 1 ? Math.sin(a) : k == 2 ? Math.sin(a) : 0);
                Vector4f p = VIEW_PROJ.transform(new Vector4f((float) px, (float) py, (float) pz, 1f));
                float u = p.x / p.w * 0.5f + 0.5f;
                float v = p.y / p.w * 0.5f + 0.5f;
                assertTrue(u >= r.minU() - EPS && u <= r.maxU() + EPS, "u " + u + " outside " + r);
                assertTrue(v >= r.minV() - EPS && v <= r.maxV() + EPS, "v " + v + " outside " + r);
            }
        }
    }

    @Test
    void sphereBehindTheCameraIsSkipped() {
        assertTrue(of(0, 0, 20, 1).empty());
    }

    @Test
    void cameraInsideOrTouchingTheSphereDrawsFull() {
        assertSame(ScreenRect.FULL, of(0, 0, 0, 3));
        assertSame(ScreenRect.FULL, of(0, 0, -1, 2));
    }

    @Test
    void sphereOffToTheSideIsSkipped() {
        assertTrue(of(100, 0, -10, 1).empty());
        assertTrue(of(0, -100, -10, 1).empty());
    }

    @Test
    void partlyOnScreenIsClamped() {
        ScreenRect r = of(10, 0, -10, 2);
        assertFalse(r.empty());
        assertEquals(1f, r.maxU(), EPS);
        assertTrue(r.minU() > 0.5f);
    }

    @Test
    void bigNearbySphereCountsAsFull() {
        assertTrue(of(0, 0, -6, 4).full());
    }

    @Test
    void noRadiusMeansWholeScreen() {
        assertSame(ScreenRect.FULL, of(0, 0, -5, 0));
    }
}
