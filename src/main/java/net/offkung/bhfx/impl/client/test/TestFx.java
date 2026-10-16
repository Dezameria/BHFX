package net.offkung.bhfx.impl.client.test;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhfx.BHFxMod;
import net.offkung.bhfx.api.FxParam;
import net.offkung.bhfx.api.client.Fx;
import net.offkung.bhfx.api.client.FxContext;
import net.offkung.bhfx.api.client.FxInit;
import net.offkung.bhfx.api.client.FrameContext;
import net.offkung.bhfx.api.client.PassList;
import net.offkung.bhfx.api.client.ScreenParams;
import net.offkung.bhfx.api.timeline.Timeline;

/**
 * Built-in effect for /bhfx_test: a warning ring, then a column of light that terrain hides correctly, a shock ring and
 * cooling ground. Exercises the whole 0.1 pipeline (network, timeline, depth reconstruction, composite, shake).
 */
public final class TestFx extends Fx {
    private static final Timeline T = Timeline.ticks()
            .phase("warn", 0, 40)
            .event("impact", 40)
            .phase("beam", 40, 100)
            .phase("heat", 40, 160)
            .build();

    private final float radius;

    public TestFx(FxInit init) {
        super(init);
        this.radius = params().get(FxParam.RADIUS, 8f);
    }

    @Override
    protected int defaultDuration() {
        return 160;
    }

    @Override
    protected void tick(FxContext ctx, int age) {
        Vec3 at = init().target();
        if (T.in("warn", age) && age % 4 == 0) {
            double a = random().nextDouble() * Math.PI * 2.0;
            ctx.particle(ParticleTypes.FLAME, at.add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius), new Vec3(0, 0.05, 0));
        }
        if (T.at("impact", age)) {
            ctx.sound(SoundEvents.GENERIC_EXPLODE, at, 3f, 0.8f);
            ctx.shake(at, 0.9f, 48.0 + radius * 4.0);
            for (int i = 0; i < 48; i++) {
                double a = random().nextDouble() * Math.PI * 2.0;
                double r = radius * 0.3 * random().nextDouble();
                ctx.particle(ParticleTypes.LAVA, at.add(Math.cos(a) * r, 0.2, Math.sin(a) * r), Vec3.ZERO);
            }
        }
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        float t = ticks(frame);
        float warn = T.env("warn", t, 6, 3);
        float beam = T.env("beam", t, 1, 14);
        float since = T.since("impact", t);
        float heat = since < 0f ? 0f : T.decay("heat", t, 30);
        float fade = 1f - stopProgress(frame);
        Vec3 target = frame.rel(init().target());
        out.add(BHFxMod.id("test"), u -> u
                .vec3("Target", target)
                .f4("P0", radius, warn, beam, since)
                .f4("P1", seedF(), fade, heat, 0f));
    }

    @Override
    protected void screen(FrameContext frame, ScreenParams screen) {
        float t = ticks(frame);
        float beam = T.env("beam", t, 1, 14);
        screen.flash(0xFFE2B0, T.pulse("impact", t, 0, 5) * 0.9f)
                .vignette(0x5A1006, 0.45f * beam)
                .aberration(0.6f * T.pulse("impact", t, 0, 12))
                .shake(0.25f * beam);
    }
}
