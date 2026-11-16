package net.deizyn.bhfx.impl.render;

import net.deizyn.bhfx.api.client.FxPose;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoAnimationLoaderTest {
    private static GeoAnimationLoader.Clip clip(String loop, String bones) {
        String json = "{\"format_version\":\"1.8.0\",\"animations\":{\"animation.t.a\":{" + loop + "\"bones\":{" + bones
                + "}}}}";
        return GeoAnimationLoader.load(json).clips().get("animation.t.a");
    }

    /** The bone's channels after applying {@code c} at {@code t} to a fresh pose. */
    private static float[] at(GeoAnimationLoader.Clip c, float t, String bone) {
        FxPose pose = new FxPose();
        c.apply(t, pose, 1f);
        return pose.channels(bone);
    }

    @Test
    void linearBetweenKeysHeldOutside() {
        GeoAnimationLoader.Clip c = clip("", "\"b\":{\"rotation\":{\"0.5\":[0,0,0],\"1.5\":[10,20,-40]}}");
        assertEquals(1.5f, c.length());
        assertEquals(5f, at(c, 1.0f, "b")[0], 1e-5f);
        assertEquals(-20f, at(c, 1.0f, "b")[2], 1e-5f);
        assertEquals(0f, at(c, 0.1f, "b")[1], 1e-5f); // before the first key
        assertEquals(20f, at(c, 9f, "b")[1], 1e-5f); // once: held at the end
    }

    @Test
    void loopWrapsAndHoldClamps() {
        GeoAnimationLoader.Clip loop = clip("\"loop\":true,\"animation_length\":2,",
                "\"b\":{\"position\":{\"0\":[0,0,0],\"2\":[0,8,0]}}");
        assertTrue(loop.loops());
        assertEquals(2f, at(loop, 2.5f, "b")[4], 1e-5f);
        GeoAnimationLoader.Clip hold = clip("\"loop\":\"hold_on_last_frame\",\"animation_length\":2,",
                "\"b\":{\"position\":{\"0\":[0,0,0],\"2\":[0,8,0]}}");
        assertEquals(GeoAnimationLoader.Loop.HOLD, hold.loop());
        assertEquals(8f, at(hold, 2.5f, "b")[4], 1e-5f);
    }

    @Test
    void prePostJumpsAndStepHolds() {
        GeoAnimationLoader.Clip c = clip("", "\"b\":{\"position\":{\"0\":[0,0,0],"
                + "\"1\":{\"pre\":{\"vector\":[0,4,0]},\"post\":[0,10,0]},\"2\":[0,10,0]},"
                + "\"rotation\":{\"0\":{\"vector\":[0,0,0],\"lerp_mode\":\"step\"},\"1\":[90,0,0]}}");
        assertEquals(2f, at(c, 0.5f, "b")[4], 1e-5f); // towards pre 4
        assertEquals(10f, at(c, 1.0f, "b")[4], 1e-5f); // post from the key on
        assertEquals(0f, at(c, 0.99f, "b")[0], 1e-5f); // step holds the earlier value
        assertEquals(90f, at(c, 1.0f, "b")[0], 1e-5f);
    }

    @Test
    void easingOfTheLaterKey() {
        GeoAnimationLoader.Clip c = clip("", "\"b\":{\"rotation\":{\"0\":[0,0,0],"
                + "\"1\":{\"vector\":[100,0,0],\"easing\":\"easeInOutSine\"}}}");
        float expected = (float) (100.0 * -(Math.cos(Math.PI * 0.25) - 1.0) / 2.0);
        assertEquals(expected, at(c, 0.25f, "b")[0], 1e-3f);
        assertEquals(50f, at(c, 0.5f, "b")[0], 1e-3f);
        assertEquals(0.0, GeoAnimationLoader.ease(GeoAnimationLoader.easing("easeOutQuad"), 0.0, 0), 1e-9);
        assertEquals(0.75, GeoAnimationLoader.ease(GeoAnimationLoader.easing("easeOutQuad"), 0.5, 0), 1e-9);
        assertEquals(1.0, GeoAnimationLoader.ease(GeoAnimationLoader.easing("easeOutBounce"), 1.0, 0), 1e-9);
        assertEquals(-1, GeoAnimationLoader.easing("wobble"));
    }

    @Test
    void catmullRomPassesThroughKeysAndIsSmooth() {
        GeoAnimationLoader.Clip c = clip("", "\"b\":{\"rotation\":{"
                + "\"0\":{\"vector\":[0,0,0],\"lerp_mode\":\"catmullrom\"},"
                + "\"1\":{\"vector\":[10,0,0],\"lerp_mode\":\"catmullrom\"},"
                + "\"2\":{\"vector\":[0,0,0],\"lerp_mode\":\"catmullrom\"}}}");
        assertEquals(10f, at(c, 1f, "b")[0], 1e-4f);
        float before = at(c, 0.99f, "b")[0];
        float after = at(c, 1.01f, "b")[0];
        assertTrue(before > 9.9f && after > 9.9f, "smooth over the top, no corner");
    }

    @Test
    void bezierWithFlatHandlesStartsAndEndsSlow() {
        GeoAnimationLoader.Clip c = clip("", "\"b\":{\"position\":{"
                + "\"0\":{\"vector\":[0,0,0],\"easing\":\"bezier\",\"right\":[0,0,0],\"right_time\":[0.4,0.4,0.4]},"
                + "\"1\":{\"vector\":[0,10,0],\"easing\":\"bezier\",\"left\":[0,0,0],\"left_time\":[-0.4,-0.4,-0.4]}}}");
        assertEquals(5f, at(c, 0.5f, "b")[4], 1e-3f);
        assertTrue(at(c, 0.1f, "b")[4] < 1f, "eases in");
        assertTrue(at(c, 0.9f, "b")[4] > 9f, "eases out");
    }

    @Test
    void molangScalarsAndShortNames() {
        GeoAnimationLoader.Library lib = GeoAnimationLoader.load("{\"animations\":{\"animation.t.spin\":{\"loop\":true,"
                + "\"animation_length\":4,\"bones\":{\"b\":{\"rotation\":[0,\"query.anim_time * 90\",0],\"scale\":1.5,"
                + "\"position\":[\"variable.nope\",0,0]}}}}}");
        GeoAnimationLoader.Clip c = lib.clips().get("spin");
        assertNotNull(c);
        assertSame(c, lib.clips().get("animation.t.spin"));
        float[] ch = at(c, 1f, "b");
        assertEquals(90f, ch[1], 1e-4f);
        assertEquals(1.5f, ch[6], 1e-6f);
        assertEquals(1.5f, ch[8], 1e-6f);
        assertEquals(0f, ch[3], 0f); // unsupported Molang reads as 0 ...
        assertEquals(1, lib.warnings().size()); // ... with one warning
        assertEquals(0f, c.length() - 4f);
    }

    @Test
    void weightBlendsOverThePose() {
        GeoAnimationLoader.Clip c = clip("", "\"b\":{\"rotation\":[40,0,0],\"scale\":[3,3,3]}");
        FxPose pose = new FxPose().rotation("b", 20, 0, 0);
        c.apply(0f, pose, 0.5f);
        assertEquals(30f, pose.channels("b")[0], 1e-5f);
        assertEquals(2f, pose.channels("b")[6], 1e-5f); // scale blends from 1
        FxPose untouched = new FxPose();
        c.apply(0f, untouched, 0f);
        assertNull(untouched.channels("b"));
    }

    @Test
    void everyBhspellsStyleFeatureInTheTestFileLoads() throws IOException {
        try (InputStream in = GeoAnimationLoaderTest.class.getResourceAsStream(
                "/assets/bhfx/animations/test_model.animation.json")) {
            assertNotNull(in);
            GeoAnimationLoader.Library lib = GeoAnimationLoader.load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(lib.warnings().isEmpty(), lib.warnings().toString());
            assertNotNull(lib.clips().get("swing"));
            assertNotNull(lib.clips().get("pop"));
            FxPose pose = new FxPose();
            for (float t = 0f; t < 4f; t += 0.05f) {
                lib.clips().get("swing").apply(t, pose, 1f);
                for (String bone : new String[]{"root", "blade", "gem"}) {
                    for (float v : pose.channels(bone)) {
                        assertTrue(Float.isFinite(v));
                    }
                }
            }
        }
    }
}
