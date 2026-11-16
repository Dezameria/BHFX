package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.BHFxClient;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxBolt;
import net.deizyn.bhfx.api.client.FxContext;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.FxTexture;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_bolt), checks {@link FxBolt} and the {@code bhfx:beam}
 * material:
 * <ul>
 *   <li>left: a forked bolt from 7 blocks up to the ground, redrawn with a new shape every 3 ticks (it flickers),
 *       with a light flashing where it lands;</li>
 *   <li>middle: a thin bolt between two points in the air, no forks, redrawn every 2 ticks;</li>
 *   <li>front: a straight beam along x drawn once for the whole effect, a scrolling noise texture and a bright core
 *       ({@code bhfx:beam}).</li>
 * </ul>
 */
public final class BoltTestFx extends Fx {
    private static final FxTexture NOISE = FxTexture.of(BHFxMod.id("textures/fx/noise_fbm.png"));

    private final FxParticles bolts;
    private final FxParticles beam;
    private final FxBolt strike = FxBolt.between(Vec3.ZERO, Vec3.ZERO);
    private final FxBolt arc = FxBolt.between(Vec3.ZERO, Vec3.ZERO);
    private boolean beamDrawn;
    private Vec3 landing = Vec3.ZERO;
    private float lastStrike = -10f;

    public BoltTestFx(FxInit init) {
        super(init);
        bolts = createParticles(FxParticles.RIBBON, 2048)
                .facing(FxParticles.Facing.RIBBON).blend(FxParticles.Blend.ADD);
        beam = createParticles(FxParticles.BEAM, 64)
                .facing(FxParticles.Facing.RIBBON).blend(FxParticles.Blend.ADD);
        BHFxClient.preload(NOISE);
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
        if (!beamDrawn) {
            beamDrawn = true;
            FxBolt.between(c.add(-4, 0.7, 1.5), c.add(4, 0.7, 1.5)).jitter(0f).segments(8).width(0.5f).taper(1f)
                    .color(0xFF6030, 1f).life(duration() > 0 ? duration() / 20f : 30f).draw(beam, random());
        }
        if (age % 3 == 0) {
            landing = c.add(-3, 0, -0.5);
            strike.ends(c.add(-3 + (random().nextDouble() - 0.5), 7, -0.5), landing)
                    .jitter(0.9f).forks(3).width(0.16f).color(0xA8D0FF, 1f).life(0.14f).draw(bolts, random());
            lastStrike = age / 20f;
        }
        if (age % 2 == 0) {
            arc.ends(c.add(-0.5, 1.2, -1.5), c.add(3.5, 3.0, -1.5))
                    .jitter(0.35f).width(0.07f).taper(1f).color(0xE0A0FF, 1f).life(0.1f).draw(bolts, random());
        }
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        out.particles(bolts);
        out.particles(beam, PassOptions.PARTICLES, u -> u.texture("Sprite", NOISE).f4("BeamUv", 0.35f, 1.2f, 1f, 0.6f));
        float since = seconds(frame) - lastStrike;
        float flash = Math.max(0f, 1f - since / 0.2f) * (1f - stopProgress(frame));
        out.light(landing.add(0, 0.5, 0), 0xA8D0FF, 2.2f * flash, 7f);
        out.light(frame.position().add(0, 0.9, 1.5), 0xFF6030, 0.7f * (1f - stopProgress(frame)), 5f);
    }
}
