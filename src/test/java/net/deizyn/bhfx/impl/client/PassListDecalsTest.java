package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.api.client.FxTexture;
import net.deizyn.bhfx.api.client.PassList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassListDecalsTest {
    private static FxTexture tex(int i) {
        return FxTexture.of(new ResourceLocation("test", "textures/t" + i + ".png"));
    }

    @Test
    void marksArePackedAfterTheLightsWithTheirAxes() {
        PassListImpl list = new PassListImpl();
        list.clear(new Vec3(10, 0, 10));
        list.decal(tex(0), new Vec3(12, 0, 10), 2f).rotation(90f).mask().glow(2f).color(0xFFFFFF, 0.5f);
        list.decal(tex(0), new Vec3(10, 3, 5), 1f).facing(new Vec3(0, 0, 1)).frame(4, 4, 5);
        list.light(new Vec3(10, 1, 10), 0xFFFFFF, 1f, 3f);
        List<PassListImpl.Entry> draw = list.drawList(0);
        assertEquals(2, draw.size());
        assertEquals("bhfx:lights", draw.get(0).shader().toString());
        assertEquals("bhfx:decals", draw.get(1).shader().toString());
        float[] d = list.decalData();
        // first mark: on the ground, turned a quarter: u = east turned to north (0, 0, -1), v = (-1, 0, 0)
        assertEquals(2f, d[0], 1e-5);
        assertEquals(2f, d[3], 1e-5);
        assertEquals(0f, d[4], 1e-5);
        assertEquals(-1f, d[6], 1e-5);
        assertEquals(-1f, d[8], 1e-5);
        assertEquals(0 + 8 + 16, (int) d[11]);
        assertEquals(2f, d[12], 1e-5); // white * glow 2
        assertEquals(0.5f, d[15], 1e-5);
        // second mark: on a wall facing south: u level (east), v up; cell 5 of a 4x4 grid = column 1, row 1
        assertEquals(1f, d[20 + 4], 1e-5);
        assertEquals(1f, d[20 + 9], 1e-5);
        assertEquals(0.25f, d[20 + 16], 1e-5);
        assertEquals(0.25f, d[20 + 17], 1e-5);
        assertEquals(0.25f, d[20 + 18], 1e-5);
    }

    @Test
    void capsOnMarksAndTextures() {
        PassListImpl list = new PassListImpl();
        list.clear(Vec3.ZERO);
        for (int i = 0; i < PassList.MAX_DECAL_TEXTURES + 3; i++) {
            list.decal(tex(i), new Vec3(i, 0, 0), 1f).color(0xFF0000, 1f); // calls on a dropped mark do nothing
        }
        assertEquals(PassList.MAX_DECAL_TEXTURES, list.decalCount());
        for (int i = 0; i < PassList.MAX_DECALS * 2; i++) {
            list.decal(tex(0), new Vec3(500 - i, 0, 0), 1f);
        }
        assertEquals(PassList.MAX_DECALS, list.decalCount());
        list.decal(tex(0), new Vec3(1, 0, 0), 0f);
        list.decal(null, new Vec3(1, 0, 0), 1f).glow(1f);
        assertEquals(PassList.MAX_DECALS, list.decalCount());
        list.clear(Vec3.ZERO);
        assertTrue(list.isEmpty());
    }
}
