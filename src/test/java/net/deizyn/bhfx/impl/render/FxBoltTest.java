package net.deizyn.bhfx.impl.render;

import net.deizyn.bhfx.api.Follow;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxBolt;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxParticles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxBoltTest {
    private static final ResourceLocation ID = new ResourceLocation("bhfx", "test");

    private static ParticleBatch batch(int capacity) {
        Fx fx = new Fx(new FxInit(ID, -1, Vec3.ZERO, Vec3.ZERO, -1, -1, Follow.NONE, 1L, 100, 0, FxParams.EMPTY)) {
        };
        ParticleBatch b = new ParticleBatch(fx, ID, capacity);
        b.facing(FxParticles.Facing.RIBBON);
        return b;
    }

    @Test
    void drawsAsManyPointsAsItSaysAndKeepsItsEnds() {
        ParticleBatch b = batch(1024);
        Vec3 from = new Vec3(1, 10, 2);
        Vec3 to = new Vec3(4, 0, -3);
        FxBolt bolt = FxBolt.between(from, to).segments(20).jitter(1f).forks(3);
        assertEquals(32 + 1 + 3 * (8 + 1), bolt.points()); // 20 rounds up to 32; forks have a quarter
        bolt.draw(b, RandomSource.create(7L));
        assertEquals(bolt.points(), b.filled);
        // the main line starts and ends exactly on the ends (positions are stored relative to the batch origin)
        float[] d = b.data;
        int last = 32 * ParticleBatch.FLOATS;
        assertEquals(to.x - from.x, d[last] - d[0], 1e-4);
        assertEquals(to.y - from.y, d[last + 1] - d[1], 1e-4);
        assertEquals(to.z - from.z, d[last + 2] - d[2], 1e-4);
        // and strays sideways in between, but no farther than the halving allows (1 + 0.55 + 0.55^2 ... < 2.3 per axis)
        double far = 0;
        Vec3 dir = to.subtract(from).normalize();
        for (int i = 0; i <= 32; i++) {
            int o = i * ParticleBatch.FLOATS;
            Vec3 p = new Vec3(d[o] - d[0], d[o + 1] - d[1], d[o + 2] - d[2]);
            far = Math.max(far, p.subtract(dir.scale(p.dot(dir))).length());
        }
        assertTrue(far > 0.2 && far < 3.3, "sideways reach " + far);
    }

    @Test
    void aBeamIsStraightAndTheSameSeedGivesTheSameBolt() {
        ParticleBatch beam = batch(64);
        FxBolt.between(new Vec3(0, 1, 0), new Vec3(8, 1, 0)).jitter(0f).segments(8).draw(beam, RandomSource.create(1L));
        assertEquals(9, beam.filled);
        for (int i = 0; i <= 8; i++) {
            int o = i * ParticleBatch.FLOATS;
            assertEquals(beam.data[1], beam.data[o + 1], 1e-5);
            assertEquals(beam.data[2], beam.data[o + 2], 1e-5);
        }
        ParticleBatch a = batch(256);
        ParticleBatch c = batch(256);
        FxBolt.between(Vec3.ZERO, new Vec3(0, 6, 0)).forks(2).draw(a, RandomSource.create(42L));
        FxBolt.between(Vec3.ZERO, new Vec3(0, 6, 0)).forks(2).draw(c, RandomSource.create(42L));
        assertEquals(a.filled, c.filled);
        for (int i = 0; i < a.filled; i++) {
            int o = i * ParticleBatch.FLOATS;
            assertEquals(a.data[o], c.data[o], 0f);
            assertEquals(a.data[o + 1], c.data[o + 1], 0f);
        }
    }
}
