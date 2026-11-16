package net.deizyn.bhfx.impl.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoModelLoaderTest {
    private static final int F = GeoModelLoader.FLOATS;

    private static String geo(String bones) {
        return "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.t\","
                + "\"texture_width\":64,\"texture_height\":32},\"bones\":[" + bones + "]}]}";
    }

    /** Every triangle turns counter-clockwise round its own normal, and the normal points away from {@code centre}. */
    private static void outward(GeoModelLoader.Data d, float cx, float cy, float cz) {
        float[] v = d.vertices();
        for (int t = 0; t < d.vertexCount() / 3; t++) {
            int a = t * 3 * F;
            int b = a + F;
            int c = b + F;
            float e1x = v[b] - v[a], e1y = v[b + 1] - v[a + 1], e1z = v[b + 2] - v[a + 2];
            float e2x = v[c] - v[a], e2y = v[c + 1] - v[a + 1], e2z = v[c + 2] - v[a + 2];
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;
            float dot = nx * v[a + 3] + ny * v[a + 4] + nz * v[a + 5];
            assertTrue(dot > 0f, "triangle " + t + " winds the wrong way");
            float mx = (v[a] + v[b] + v[c]) / 3f - cx;
            float my = (v[a + 1] + v[b + 1] + v[c + 1]) / 3f - cy;
            float mz = (v[a + 2] + v[b + 2] + v[c + 2]) / 3f - cz;
            assertTrue(mx * v[a + 3] + my * v[a + 4] + mz * v[a + 5] > 0f, "triangle " + t + " faces inward");
        }
    }

    @Test
    void oneBoxCubeLikeGeckoLib() {
        GeoModelLoader.Data d = GeoModelLoader.load(geo(
                "{\"name\":\"b\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[2,4,6],\"uv\":[0,0]}]}"));
        assertEquals(36, d.vertexCount());
        // x is mirrored: origin.x = -(0 + 2) / 16
        assertArrayEquals(new float[]{-2 / 16f, 0f, 0f}, d.min(), 1e-6f);
        assertArrayEquals(new float[]{0f, 4 / 16f, 6 / 16f}, d.max(), 1e-6f);
        outward(d, -1 / 16f, 2 / 16f, 3 / 16f);
        assertArrayEquals(new String[]{"b"}, d.boneNames());
    }

    @Test
    void boxUvOfTheNorthFace() {
        GeoModelLoader.Data d = GeoModelLoader.load(geo(
                "{\"name\":\"b\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[2,4,6],\"uv\":[8,4]}]}"));
        float[] v = d.vertices();
        // faces in order west, east, north, ...: the north face is triangles 4 and 5
        float uMin = 1, uMax = 0, vMin = 1, vMax = 0;
        for (int i = 12; i < 18; i++) {
            uMin = Math.min(uMin, v[i * F + 6]);
            uMax = Math.max(uMax, v[i * F + 6]);
            vMin = Math.min(vMin, v[i * F + 7]);
            vMax = Math.max(vMax, v[i * F + 7]);
            assertEquals(-1f, v[i * F + 5], 1e-6f); // facing -z
        }
        // GeckoLib: north at (u + d, v + d), size (w, h) = (8 + 6, 4 + 6), (2, 4)
        assertEquals(14 / 64f, uMin, 1e-6f);
        assertEquals(16 / 64f, uMax, 1e-6f);
        assertEquals(10 / 32f, vMin, 1e-6f);
        assertEquals(14 / 32f, vMax, 1e-6f);
    }

    @Test
    void boneRotationTurnsItsCubes() {
        // z rotation is not negated: +90 turns (x, y) to (-y, x) round the pivot
        GeoModelLoader.Data d = GeoModelLoader.load(geo(
                "{\"name\":\"b\",\"pivot\":[0,0,0],\"rotation\":[0,0,90],\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0,0]}]}"));
        assertArrayEquals(new float[]{-1 / 16f, -1 / 16f, 0f}, d.min(), 1e-6f);
        assertArrayEquals(new float[]{0f, 0f, 1 / 16f}, d.max(), 1e-6f);
        outward(d, -0.5f / 16f, -0.5f / 16f, 0.5f / 16f);
    }

    @Test
    void childBonesFollowTheirParent() {
        GeoModelLoader.Data d = GeoModelLoader.load(geo(
                "{\"name\":\"p\",\"pivot\":[0,0,0],\"rotation\":[0,0,90]},"
                        + "{\"name\":\"c\",\"parent\":\"p\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0,0]}]}"));
        assertArrayEquals(new float[]{-1 / 16f, -1 / 16f, 0f}, d.min(), 1e-6f);
        assertEquals(1f, d.vertices()[8]); // bone index of the child
    }

    @Test
    void hiddenBonesAndMissingFacesAreLeftOut() {
        GeoModelLoader.Data d = GeoModelLoader.load(geo(
                "{\"name\":\"h\",\"neverRender\":true,\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0,0]}]},"
                        + "{\"name\":\"f\",\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],"
                        + "\"uv\":{\"north\":{\"uv\":[0,0],\"uv_size\":[1,1]}}}]}"));
        assertEquals(6, d.vertexCount());
    }

    @Test
    void mirroredAndInflatedCubesStayClosed() {
        GeoModelLoader.Data d = GeoModelLoader.load(geo(
                "{\"name\":\"b\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[2,2,2],\"uv\":[0,0],"
                        + "\"mirror\":true,\"inflate\":0.5}]}"));
        assertEquals(36, d.vertexCount());
        assertEquals(-2.5f / 16f, d.min()[0], 1e-6f);
        outward(d, -1 / 16f, 1 / 16f, 1 / 16f);
    }

    @Test
    void oldFormatIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> GeoModelLoader.load("{\"format_version\":\"1.8.0\"}"));
    }

    @Test
    void theBundledTestModelLoads() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/assets/bhfx/geo/test_model.geo.json")) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            GeoModelLoader.Data d = GeoModelLoader.load(json);
            assertEquals(6 * 6 * 6, d.vertexCount()); // six cubes, six faces each
            assertTrue(d.max()[1] > 1.6f && d.max()[1] < 1.8f, "the sword is about 27 pixels tall");
        }
    }
}
