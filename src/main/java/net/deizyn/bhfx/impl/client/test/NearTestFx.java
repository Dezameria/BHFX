package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxContext;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.PassList;

/**
 * Built-in effects for /bhfx test and /bhfx_play, check {@link FxParticles#nearFade} and
 * {@link FxParticles#maxScreenSize}: a thick cloud of big smoke puffs (about 400 alive, 1.2 to 2 blocks across) with
 * glowing motes in it, four blocks round the target. Meant to be stood in. {@code bhfx:test_near} fades the puffs
 * within 2.5 blocks of the camera (gone within 0.8) and keeps them under 60% of the screen's height;
 * {@code bhfx:test_near_off} does neither.
 */
public final class NearTestFx extends Fx {
    private final FxParticles smoke;
    private final FxParticles motes;

    public NearTestFx(FxInit init, boolean near) {
        super(init);
        smoke = createParticles(FxParticles.DOT, 1024)
                .blend(FxParticles.Blend.ALPHA).drag(0.6f)
                .alphaOverLife(0f, 0.6f, 0.6f, 0f).sizeOverLife(0.7f, 1.2f);
        motes = createParticles(FxParticles.DOT, 1024).drag(0.5f).alphaOverLife(0f, 1f, 0f);
        if (near) {
            smoke.nearFade(0.8f, 2.5f).maxScreenSize(0.6f);
            motes.nearFade(0.3f, 1.0f);
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
        for (int i = 0; i < 5; i++) {
            double a = random().nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(random().nextDouble()) * 4.0;
            smoke.spawn(c.x + Math.cos(a) * r, c.y + 0.3 + random().nextDouble() * 2.2, c.z + Math.sin(a) * r)
                    .velocity((random().nextDouble() - 0.5) * 0.4, 0.15, (random().nextDouble() - 0.5) * 0.4)
                    .life(4f).size(0.6f + random().nextFloat() * 0.4f)
                    .color(0x8A8F9C, 0.5f);
            motes.spawn(c.x + Math.cos(a) * r, c.y + 0.3 + random().nextDouble() * 2.2, c.z + Math.sin(a) * r)
                    .velocity((random().nextDouble() - 0.5) * 0.6, 0.3, (random().nextDouble() - 0.5) * 0.6)
                    .life(3f).size(0.05f)
                    .color(0x9FE0FF, 1f);
        }
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        out.particles(smoke);
        out.particles(motes);
    }
}
