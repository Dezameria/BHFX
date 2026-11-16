package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxContext;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxModel;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_debris), checks {@link PassList#modelParticles}: every
 * 2.5 s
 * <ul>
 *   <li>48 swords are thrown up from the target, each in a random pose and tumbling, fall back under gravity, shrink
 *       and fade at the end of their life ({@code TUMBLE} batch, textured material);</li>
 *   <li>24 glowing swords fly outwards in a ring, pointing along their flight and rolling ({@code VELOCITY} batch,
 *       glow material), slowed by drag.</li>
 * </ul>
 * The batches are only the motion: they are not drawn as sprites.
 */
public final class DebrisTestFx extends Fx {
    private static final FxModel SWORD = FxModel.geo(BHFxMod.id("test_model"))
            .texture(BHFxMod.id("textures/fx/test_model.png"));

    private final FxParticles thrown;
    private final FxParticles ring;

    public DebrisTestFx(FxInit init) {
        super(init);
        thrown = createParticles(FxParticles.DOT, 256)
                .facing(FxParticles.Facing.TUMBLE).gravity(0, -14, 0)
                .sizeOverLife(1f, 1f, 1f, 0f).alphaOverLife(1f, 1f, 1f, 0.2f);
        ring = createParticles(FxParticles.DOT, 128)
                .facing(FxParticles.Facing.VELOCITY).drag(1.2f)
                .alphaOverLife(1f, 1f, 0f);
    }

    @Override
    protected int defaultDuration() {
        return 400;
    }

    @Override
    protected void tick(FxContext ctx, int age) {
        if (stopping() || age % 50 != 0) {
            return;
        }
        Vec3 c = ctx.position();
        for (int i = 0; i < 48; i++) {
            double a = random().nextDouble() * Math.PI * 2.0;
            double out = 1.0 + random().nextDouble() * 3.5;
            thrown.spawn(c.x, c.y + 0.3, c.z)
                    .velocity(Math.cos(a) * out, 9.0 + random().nextDouble() * 4.0, Math.sin(a) * out)
                    .life(2.2f).size(0.35f + random().nextFloat() * 0.3f)
                    .tumble(random().nextDouble() - 0.5, random().nextDouble() - 0.5, random().nextDouble() - 0.5,
                            4f + random().nextFloat() * 6f)
                    .color(0xFFFFFF, 1f);
        }
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2.0;
            ring.spawn(c.x + Math.cos(a) * 0.6, c.y + 1.4, c.z + Math.sin(a) * 0.6)
                    .velocity(Math.cos(a) * 9.0, 0.4, Math.sin(a) * 9.0)
                    .life(1.6f).size(0.5f).rotation(0f, 6f)
                    .color(0x66D0FF, 1f);
        }
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        float fade = 1f - stopProgress(frame);
        out.modelParticles(SWORD, thrown, 1f, null, FxModel.TEXTURED, PassOptions.PARTICLES,
                u -> u.f4("MColor", 1f, 1f, 1f, fade));
        out.modelParticles(SWORD, ring, 1f, null, FxModel.GLOW, PassOptions.PARTICLES,
                u -> u.f4("MColor", 1.6f * fade, 1.6f * fade, 1.6f * fade, 1f).f4("MParams", 1f, 0f, 0f, 0f));
    }
}
