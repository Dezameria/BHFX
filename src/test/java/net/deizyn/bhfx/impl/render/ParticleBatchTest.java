package net.deizyn.bhfx.impl.render;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.Follow;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleBatchTest {
    private static final ResourceLocation ID = new ResourceLocation("bhfx", "test");

    private static Fx fx() {
        return new Fx(new FxInit(ID, -1, Vec3.ZERO, Vec3.ZERO, -1, -1, Follow.NONE, 1L, 100, 0, FxParams.EMPTY)) {
        };
    }

    @AfterEach
    void resetClock() {
        ParticleBatch.clockOffset(1f);
    }

    @Test
    void ringReplacesOldestAndTracksPendingRun() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 4);
        ParticleBatch.clockOffset(0f);
        for (int i = 0; i < 3; i++) {
            b.spawn(i, 0, 0);
        }
        assertEquals(3, b.filled);
        assertEquals(3, b.pending);
        assertEquals(0, b.pendingStart());

        b.pending = 0; // as after an upload
        b.needsFull = false;
        for (int i = 0; i < 3; i++) {
            b.spawn(10 + i, 0, 0); // slots 3, 0, 1: the run wraps
        }
        assertEquals(4, b.filled);
        assertEquals(3, b.pending);
        assertEquals(3, b.pendingStart());
        // slot 0 now holds the 5th spawn (x = 11), relative to the origin (first spawn, x = 0)
        assertEquals(11f, b.data[0]);
    }

    @Test
    void spawnValuesAndIdle() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 8);
        ParticleBatch.clockOffset(0f);
        assertTrue(b.idleAt(b.now()), "nothing spawned");
        b.spawn(new Vec3(5, 64, 5)).velocity(0, 2, 0).life(0.5f).size(0.1f, 0.3f).color(1f, 2f, 3f, 0.5f).delay(0.25f);
        int s = 0;
        assertEquals(0f, b.data[s]);
        assertEquals(0.25f, b.data[s + 3], 1e-6, "birth = now (0) + delay");
        assertEquals(0.5f, b.data[s + 7]);
        assertEquals(0.3f, b.data[s + 9]);
        assertEquals(2f, b.data[s + 13]);
        assertFalse(b.idleAt(0f));
        assertTrue(b.idleAt(0.8f), "dead after birth + life");
        assertEquals(0, b.alive(), "not born yet at 0 s (delay 0.25 s)");
        ParticleBatch.clockOffset(8f); // age 0 + 8 ticks = 0.4 s
        assertEquals(1, b.alive());
        b.clear();
        assertTrue(b.idleAt(0f));
    }

    @Test
    void axisLengthCountsForTheCullSphere() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 8);
        b.facing(net.deizyn.bhfx.api.client.FxParticles.Facing.AXIS).pivot(1f);
        b.spawn(0, 0, 0).axis(1, 0, 0, 12f).size(0.5f).life(1f);
        assertEquals(12f, b.data[23]);
        b.idleAt(0f);
        // length 12 + half-width 0.5 + margin 0.5
        assertEquals(13f, b.cullRadius(), 1e-4);
    }

    @Test
    void cullRadiusCoversTravelAndGravity() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 8);
        b.gravity(0, -10, 0);
        b.spawn(0, 0, 0).velocity(3, 0, 0).life(2f).size(0.5f);
        b.idleAt(0f);
        // reach 0 + 3*2 + 0.5, gravity 0.5*10*4 = 20, plus 0.5 margin
        assertEquals(27f, b.cullRadius(), 1e-4);
    }

    @Test
    void ribbonsLinkTheirOwnPointsEvenWhenInterleaved() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 16);
        b.facing(net.deizyn.bhfx.api.client.FxParticles.Facing.RIBBON);
        net.deizyn.bhfx.api.client.FxParticles.Ribbon r1 = b.ribbon();
        net.deizyn.bhfx.api.client.FxParticles.Ribbon r2 = b.ribbon();
        r1.spawn(0, 0, 0); // slot 0
        r2.spawn(5, 0, 0); // slot 1
        r1.spawn(1, 0, 0); // slot 2, after slot 0
        r2.spawn(6, 0, 0); // slot 3, after slot 1
        int f = ParticleBatch.FLOATS;
        assertEquals(0f, b.data[27], "first point has no link");
        assertEquals(1f, b.data[2 * f + 27], "slot 2 follows slot 0 (stored + 1)");
        assertEquals(2f, b.data[3 * f + 27], "slot 3 follows slot 1");
        assertEquals(b.data[20], b.data[2 * f + 20], "same ribbon id");
        assertTrue(b.data[20] != b.data[f + 20], "different ribbons");
        r1.cut();
        r1.spawn(2, 0, 0); // slot 4
        assertEquals(0f, b.data[4 * f + 27], "cut starts a new chain");
        assertTrue(b.data[4 * f + 20] != b.data[20]);
    }

    @Test
    void ribbonLinksAreOnlyWrittenInRibbonBatches() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 8);
        b.facing(net.deizyn.bhfx.api.client.FxParticles.Facing.AXIS);
        b.ribbon().spawn(0, 0, 0).axis(0, 0, 1, 2f);
        b.ribbon().spawn(0, 0, 0);
        assertEquals(1f, b.data[ParticleBatch.FLOATS + 21], "default axis untouched (up)");
        assertEquals(0f, b.data[ParticleBatch.FLOATS + 27]);
    }

    @Test
    void streamPointsFormOneRibbonUntilReset() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 64);
        b.facing(net.deizyn.bhfx.api.client.FxParticles.Facing.RIBBON);
        ParticleBatch.clockOffset(0f);
        net.deizyn.bhfx.api.client.FxParticles.Stream st = b.stream(40f);
        net.deizyn.bhfx.api.client.FxEmitter.Shape point = net.deizyn.bhfx.api.client.FxEmitter.point();
        st.emit(point, 0, 0, 0, 0, null);
        ParticleBatch.clockOffset(2f);
        assertEquals(4, st.emit(point, 4, 0, 0, 0, null));
        int f = ParticleBatch.FLOATS;
        assertEquals(0f, b.data[27]);
        for (int i = 1; i < 4; i++) {
            assertEquals(i, b.data[i * f + 27], 1e-6, "each point follows the one before");
        }
        st.reset();
        st.emit(point, 4, 0, 0, 0, null);
        ParticleBatch.clockOffset(4f);
        st.emit(point, 8, 0, 0, 0, null);
        assertEquals(0f, b.data[4 * f + 27], "a new ribbon after reset");
        assertTrue(b.data[4 * f + 20] != b.data[20]);
    }

    @Test
    void curvesAndTurbulenceWidenTheCullSphere() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 8);
        b.sizeOverLife(0.5f, 3f).turbulence(1f, 2f).alphaOverLife(1f, 0.5f, 0f, 0f, 9f);
        assertEquals(4, b.alphaKeyCount, "at most 4 keys");
        assertEquals(3f, b.sizeCurveMax());
        b.spawn(0, 0, 0).life(1f).size(1f);
        b.idleAt(0f);
        // size 1 + margin 0.5, plus size 1 x (3 - 1) for the curve, plus 2 x turbulence 1
        assertEquals(1.5f + 2f + 2f, b.cullRadius(), 1e-4);
        b.colorOverLife(0xFF0000, 0x00FF00);
        assertEquals(2, b.colorKeyCount);
        assertEquals(1f, b.colorKeys[4], "second key green");
        b.colorOverLife(net.deizyn.bhfx.api.client.FxTexture.of(ID), 1f, 0f);
        assertEquals(0, b.colorKeyCount, "a ramp replaces the keys");
    }

    @Test
    void onGroundMovesPointsToTheSurface() {
        net.deizyn.bhfx.api.client.FxEmitter.GroundProbe old = ParticleBatch.ground;
        try {
            ParticleBatch.ground = (x, y, z, search) -> x > 0 ? 70.0 : Double.NaN;
            ParticleBatch b = new ParticleBatch(fx(), ID, 8);
            net.deizyn.bhfx.api.client.FxEmitter.Shape s =
                    net.deizyn.bhfx.api.client.FxEmitter.point().onGround(4, 0.25);
            double[] y = new double[2];
            b.emit(s, 1, 64, 0, 1, 0, (sp, p) -> y[0] = p.y());
            b.emit(s, -1, 64, 0, 1, 0, (sp, p) -> y[1] = p.y());
            assertEquals(70.25, y[0], 1e-9, "on the surface, lifted");
            assertEquals(64.0, y[1], 1e-9, "no surface found: unchanged");
            // the origin is the first spawn (y 70.25); the second sits 6.25 below it
            assertEquals(-6.25f, b.data[ParticleBatch.FLOATS + 1], 1e-4);
        } finally {
            ParticleBatch.ground = old;
        }
    }

    @Test
    void orbitAndPullAreStoredAndWidenTheCullSphere() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 8);
        b.spawn(0, 0, 0).life(1f).size(0.5f).orbit(3f, 1f).pull(-2f);
        assertEquals(3f, b.data[24]);
        assertEquals(1f, b.data[25]);
        assertEquals(-2f, b.data[26]);
        b.orbit(4, 0, 0, 0, 2, 0);
        assertEquals(1f, b.oay, 1e-6, "axis normalised");
        assertEquals(4f, b.orbitX(), "centre relative to the origin (first spawn)");
        b.idleAt(0f);
        // pushed out 2 b/s for 1 s + size 0.5 + margin 0.5 from the centre, plus |centre| from the origin
        assertEquals(3f + 4f, b.cullRadius(), 1e-4);
    }

    @Test
    void orbitingParticlesAreStoredRelativeToTheCentreSoTheyMoveWithIt() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 8);
        b.spawn(0, 0, 0).life(1f); // origin
        b.orbit(10, 0, 0, 0, 1, 0);
        b.spawn(12, 1, 0).life(1f).orbit(2f, 0f).pull(1f); // stored once, from the centre
        assertEquals(2f, b.data[ParticleBatch.FLOATS], 1e-6);
        assertEquals(1f, b.data[ParticleBatch.FLOATS + 1], 1e-6);
        b.spawn(13, 0, 0).life(1f).pull(1f).orbit(1f, 0f); // pull first: same
        assertEquals(3f, b.data[2 * ParticleBatch.FLOATS], 1e-6);
        b.spawn(12, 0, 0).life(1f).orbit(0f, 0f); // not orbiting: stays from the origin
        assertEquals(12f, b.data[3 * ParticleBatch.FLOATS], 1e-6);
        b.orbit(20, 0, 0, 0, 1, 0); // the axis moves: stored data doesn't
        assertEquals(2f, b.data[ParticleBatch.FLOATS], 1e-6);
        b.idleAt(0f);
        // one reach for both kinds (conservative): the farthest is the non-orbiting 12 + default size 0.25, + margin
        // 0.5, + |centre| 20 because some particles orbit
        assertEquals(12.25f + 0.5f + 20f, b.cullRadius(), 1e-4);
    }

    @Test
    void evenRingEmitPlacesAndAimsEveryParticle() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 64);
        int[] calls = {0};
        int n = b.emit(net.deizyn.bhfx.api.client.FxEmitter.ring(2).even(), 10, 5, 10, 4, 3.0, (s, p) -> {
            calls[0]++;
            assertEquals(2.0, Math.hypot(p.x() - 10, p.z() - 10), 1e-9, "on the ring");
            assertEquals(5.0, p.y(), 1e-9, "across the up axis");
            assertEquals(1.0, Math.hypot(p.dx(), p.dz()), 1e-9, "unit, outwards");
        });
        assertEquals(4, n);
        assertEquals(4, calls[0]);
        assertEquals(4, b.filled);
        for (int i = 0; i < 4; i++) {
            int base = i * ParticleBatch.FLOATS;
            double vx = b.data[base + 4];
            double vz = b.data[base + 6];
            assertEquals(3.0, Math.hypot(vx, vz), 1e-5, "speed along the direction");
        }
    }

    @Test
    void coneStaysWithinItsAngle() {
        net.deizyn.bhfx.api.client.FxEmitter.Shape cone = net.deizyn.bhfx.api.client.FxEmitter.cone(0.3).axis(1, 0, 0);
        double[] out = new double[6];
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(7L);
        for (int i = 0; i < 200; i++) {
            cone.sample(r, i, 200, out);
            assertTrue(out[3] >= Math.cos(0.3) - 1e-9, "within 0.3 rad of +x");
            assertEquals(1.0, Math.sqrt(out[3] * out[3] + out[4] * out[4] + out[5] * out[5]), 1e-9);
        }
    }

    @Test
    void streamSpreadsBirthsAndPositionsBetweenCalls() {
        ParticleBatch b = new ParticleBatch(fx(), ID, 256);
        ParticleBatch.clockOffset(0f);
        net.deizyn.bhfx.api.client.FxParticles.Stream st = b.stream(100f);
        net.deizyn.bhfx.api.client.FxEmitter.Shape point = net.deizyn.bhfx.api.client.FxEmitter.point();
        assertEquals(0, st.emit(point, 0, 0, 0, 0, null), "the first call only starts the clock");
        ParticleBatch.clockOffset(2f); // 0.1 s later, moved 10 blocks along x
        assertEquals(10, st.emit(point, 10, 0, 0, 0, null));
        // the first spawn is the origin; births run 0.01 .. 0.1 s, positions 1 .. 10 blocks
        assertEquals(0.01f, b.data[3], 1e-5);
        int last = 9 * ParticleBatch.FLOATS;
        assertEquals(0.1f, b.data[last + 3], 1e-5);
        assertEquals(9f, b.data[last], 1e-4, "10 blocks from the origin at x = 1");
        ParticleBatch.clockOffset(2f + 20f); // a 1 s gap: only 0.25 s is caught up
        assertEquals(25, st.emit(point, 10, 0, 0, 0, null));
        st.reset();
        assertEquals(0, st.emit(point, 10, 0, 0, 0, null));
    }
}
