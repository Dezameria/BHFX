package net.deizyn.bhfx.impl.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Merge rules of ScreenParams: stacking several effects must never add up past the strongest one. */
class ScreenParamsImplTest {
    private static final float EPS = 1e-5f;
    private ScreenParamsImpl screen;

    @BeforeEach
    void setUp() {
        FrameContextImpl frame = new FrameContextImpl();
        // a projection whose w is the camera-space z: points with z > 0 are in front
        Matrix4f viewProj = new Matrix4f().m23(1f).m33(0f);
        frame.begin(0f, Vec3.ZERO, 1920, 1080, viewProj, 1f);
        screen = new ScreenParamsImpl(frame);
        screen.reset();
    }

    @Test
    void emptyFrameIsIdle() {
        assertTrue(screen.imageIdle());
        screen.zoomBlur(0.3f);
        assertFalse(screen.imageIdle());
        screen.reset();
        assertTrue(screen.imageIdle());
    }

    @Test
    void amountsTakeTheMaximum() {
        screen.aberration(0.2f).aberration(0.7f).aberration(0.4f);
        screen.zoomBlur(0.5f).zoomBlur(0.1f);
        screen.desaturate(0.3f).desaturate(0.6f);
        screen.shake(0.4f).shake(0.2f);
        assertEquals(0.7f, screen.aberrationValue(), EPS);
        assertEquals(0.5f, screen.zoomBlurValue(), EPS);
        assertEquals(0.6f, screen.desaturateValue(), EPS);
        assertEquals(0.4f, screen.shakeValue(), EPS);
    }

    @Test
    void strongestFlashAndVignetteKeepTheirColour() {
        screen.flash(0xFF0000, 0.5f).flash(0x00FF00, 0.9f).flash(0x0000FF, 0.3f);
        screen.vignette(0x111111, 0.2f).vignette(0x222222, 0.8f);
        assertEquals(0x00FF00, screen.flashRgb());
        assertEquals(0x222222, screen.vignetteRgb());
        assertEquals(0.8f, screen.vignetteStrength(), EPS);
    }

    @Test
    void bloomTakesTheMaximumOfEachValue() {
        screen.bloom(1.2f, 1f).bloom(0.5f, 2f);
        assertEquals(1.2f, screen.bloomStrength(), EPS);
        assertEquals(2f, screen.bloomRadius(), EPS);
        screen.reset();
        screen.bloom(0.5f, 0.2f);
        assertEquals(1f, screen.bloomRadius(), EPS); // never below normal
    }

    @Test
    void ripplesAreCappedAndSkipPointsBehindTheCamera() {
        screen.ripple(new Vec3(0, 0, -10), 2f, 1f, 1f); // behind
        assertEquals(0, screen.rippleCount());
        for (int i = 0; i < ScreenParamsImpl.MAX_RIPPLES + 3; i++) {
            screen.ripple(new Vec3(i, 0, 10), 2f, 1f, 0.5f);
        }
        assertEquals(ScreenParamsImpl.MAX_RIPPLES, screen.rippleCount());
        screen.reset();
        screen.ripple(new Vec3(0, 0, 10), 2f, 1f, 0f); // no strength
        assertEquals(0, screen.rippleCount());
    }
}
