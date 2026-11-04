package net.deizyn.bhfx.impl.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.Follow;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.PassOptions;
import net.deizyn.bhfx.impl.render.ParticleBatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PassListImplTest {
    private static final ResourceLocation ID = new ResourceLocation("bhfx", "test");

    private static ParticleBatch liveBatch() {
        Fx fx = new Fx(new FxInit(ID, -1, Vec3.ZERO, Vec3.ZERO, -1, -1, Follow.NONE, 1L, 100, 0, FxParams.EMPTY)) {
        };
        ParticleBatch b = new ParticleBatch(fx, ID, 8);
        b.spawn(0, 0, 0).life(10f);
        return b;
    }

    @Test
    void particleBatchesDontCountAgainstThePassCap() {
        PassListImpl list = new PassListImpl();
        list.clear(Vec3.ZERO);
        // three effects with 8 particle batches each, as when several particle effects run at once
        for (int fx = 0; fx < 3; fx++) {
            list.currentDistance(fx * 10.0);
            for (int i = 0; i < 8; i++) {
                list.particles(liveBatch(), PassOptions.PARTICLES, u -> { });
            }
        }
        // and twelve screen passes, four over the cap
        for (int i = 0; i < 12; i++) {
            list.currentDistance(i);
            list.add(ID, u -> { });
        }
        List<PassListImpl.Entry> draw = list.drawList(8);
        long particles = draw.stream().filter(e -> e.particles() != null).count();
        long screen = draw.stream().filter(e -> e.particles() == null).count();
        assertEquals(24, particles, "every particle batch is kept");
        assertEquals(8, screen, "screen passes are capped");
        assertEquals(7.0, draw.stream().filter(e -> e.particles() == null).mapToDouble(PassListImpl.Entry::distance)
                .max().orElse(-1), 1e-9, "the farthest screen passes go first");
    }
}
