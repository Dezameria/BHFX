package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxContext;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.PassList;

/**
 * Built-in effects for /bhfx test and /bhfx_play, check {@link FxParticles#lod}: a dense fountain of small embers
 * (about 3000 alive, additive) and a column of smoke puffs (about 600 alive, alpha) above the target.
 * {@code bhfx:test_lod} thins both by distance (step 8 blocks), {@code bhfx:test_lod_off} draws every particle: side
 * by side from far away they should look alike, and from near they are the same.
 */
public final class LodTestFx extends Fx {
    private final FxParticles embers;
    private final FxParticles smoke;

    public LodTestFx(FxInit init, boolean lod) {
        super(init);
        embers = createParticles(FxParticles.DOT, 4096)
                .gravity(0, -6, 0).drag(0.4f).alphaOverLife(1f, 1f, 0f).sizeOverLife(1f, 0.4f);
        smoke = createParticles(FxParticles.DOT, 1024)
                .blend(FxParticles.Blend.ALPHA).gravity(0, 0.8, 0).drag(0.8f)
                .alphaOverLife(0f, 0.5f, 0f).sizeOverLife(0.5f, 1.6f);
        if (lod) {
            embers.lod(8f);
            smoke.lod(8f);
        }
    }

    @Override
    protected int defaultDuration() {
        return 400;
    }

    @Override
    protected void tick(FxContext ctx, int age) {
        if (stopping()) {
            return;
        }
        Vec3 c = ctx.position();
        for (int i = 0; i < 75; i++) {
            double a = random().nextDouble() * Math.PI * 2.0;
            double out = random().nextDouble() * 2.2;
            embers.spawn(c.x, c.y + 0.2, c.z)
                    .velocity(Math.cos(a) * out, 6.0 + random().nextDouble() * 3.0, Math.sin(a) * out)
                    .life(2f).size(0.05f + random().nextFloat() * 0.04f)
                    .color(random().nextBoolean() ? 0xFFB040 : 0xFF6020, 1f);
        }
        for (int i = 0; i < 10; i++) {
            double a = random().nextDouble() * Math.PI * 2.0;
            double r = random().nextDouble() * 0.8;
            smoke.spawn(c.x + Math.cos(a) * r, c.y + 0.4, c.z + Math.sin(a) * r)
                    .velocity(Math.cos(a) * 0.3, 1.2 + random().nextDouble(), Math.sin(a) * 0.3)
                    .life(3f).size(0.45f + random().nextFloat() * 0.25f)
                    .color(0x50545C, 0.55f);
        }
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        out.particles(smoke);
        out.particles(embers);
    }
}
