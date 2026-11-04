package net.deizyn.bhfx.impl.client.test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.FxParam;
import net.deizyn.bhfx.api.StopMode;
import net.deizyn.bhfx.api.client.BHFxClient;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxContext;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.FxTexture;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;
import net.deizyn.bhfx.api.client.ScreenParams;

/**
 * Built-in effect for /bhfx test (bhfx:test_particles): a magic bonfire that tests GPU particles and textures. Every
 * part uses a different feature:
 * <ul>
 *   <li>embers — ADD, camera-facing, colour ramp texture (clamped, RGBA), buoyancy and drag</li>
 *   <li>smoke — ALPHA, spinning, packed noise texture (two masks in one), fake sphere lighting, soft edges</li>
 *   <li>heat haze — DISTORT, grayscale (R8) noise texture</li>
 *   <li>bursts every 2.5 s — sparks (VELOCITY facing, gravity), a flipbook puff, a ground ring (FLAT facing)</li>
 * </ul>
 * Params: {@code radius} (default 2.5).
 */
public final class ParticlesTestFx extends Fx {
    // no .srgb(): the ramp is drawn straight onto the display-encoded frame, so its stored colours are what we want
    private static final FxTexture RAMP = FxTexture.of(tex("ramp_fire")).clamp();
    private static final FxTexture FBM = FxTexture.of(tex("noise_fbm"));
    private static final FxTexture SMOKE_NOISE = FxTexture.packed(tex("noise_fbm"), tex("noise_cells"), null, null);
    private static final FxTexture BOOK = FxTexture.of(tex("burst_flipbook")).clamp();
    private static final int BURST_EVERY = 50;
    private static final int STOP_SPAWNING_BEFORE_END = 50;

    private final float radius;
    private final FxParticles embers;
    private final FxParticles smoke;
    private final FxParticles heat;
    private final FxParticles sparks;
    private final FxParticles bursts;
    private final FxParticles rings;

    public ParticlesTestFx(FxInit init) {
        super(init);
        radius = Math.max(0.5f, params().get(FxParam.RADIUS, 2.5f));
        embers = createParticles(material("test_ember"), 2048)
                .gravity(0, 0.8, 0).drag(0.7f).softness(0.3f);
        smoke = createParticles(material("test_smoke"), 512)
                .blend(FxParticles.Blend.ALPHA).gravity(0, 0.35, 0).drag(0.5f).softness(0.8f);
        heat = createParticles(material("test_heat"), 256)
                .blend(FxParticles.Blend.DISTORT).distortion(0.12f).softness(0.5f);
        sparks = createParticles(material("test_spark"), 1024)
                .facing(FxParticles.Facing.VELOCITY).stretch(0.06f).gravity(0, -14, 0).drag(0.8f).softness(0.1f);
        bursts = createParticles(material("test_burst"), 32).softness(0.6f);
        rings = createParticles(material("test_ring"), 16).facing(FxParticles.Facing.FLAT).softness(0f);
        for (FxTexture t : new FxTexture[]{RAMP, FBM, SMOKE_NOISE, BOOK}) {
            BHFxClient.preload(t);
        }
    }

    @Override
    protected int defaultDuration() {
        return 240;
    }

    @Override
    protected int endTicks(StopMode mode) {
        // let what is already in the air finish
        return mode == StopMode.IMMEDIATE ? 0 : 60;
    }

    @Override
    protected void tick(FxContext ctx, int age) {
        int d = duration();
        boolean spawning = !stopping() && (d < 0 || age < d - STOP_SPAWNING_BEFORE_END);
        if (!spawning) {
            return;
        }
        Vec3 at = ctx.position();
        for (int i = 0; i < 4; i++) {
            Vec3 p = disk(at, radius * 0.5, 0.15);
            embers.spawn(p)
                    .velocity(spread(0.5), 1.2 + random().nextDouble() * 1.6, spread(0.5))
                    .life(1.4f + random().nextFloat() * 1.6f)
                    .size(0.05f + random().nextFloat() * 0.06f, 0.015f);
        }
        if (age % 2 == 0) {
            smoke.spawn(disk(at, radius * 0.35, 0.6))
                    .velocity(spread(0.25), 0.7 + random().nextDouble() * 0.5, spread(0.25))
                    .life(3f + random().nextFloat() * 1.5f)
                    .size(0.5f, 1.6f + random().nextFloat() * 0.6f)
                    .rotation(random().nextFloat() * 6.283f, (random().nextFloat() - 0.5f) * 0.8f)
                    .color(0x2B2A2E, 0.85f);
        }
        if (age % 3 == 0) {
            heat.spawn(disk(at, radius * 0.3, 1.0))
                    .velocity(0, 1.3, 0)
                    .life(1.6f)
                    .size(1.0f, 1.8f);
        }
        if (age >= 20 && (age - 20) % BURST_EVERY == 0) {
            burst(ctx, at);
        }
    }

    private void burst(FxContext ctx, Vec3 at) {
        ctx.sound(SoundEvents.FIRECHARGE_USE, at, 1.5f, 0.7f + random().nextFloat() * 0.2f);
        ctx.shake(at, 0.25f, 24.0);
        for (int i = 0; i < 90; i++) {
            double a = random().nextDouble() * Math.PI * 2.0;
            double up = 0.25 + random().nextDouble() * 0.75;
            double side = Math.sqrt(1.0 - up * up);
            double speed = 6.0 + random().nextDouble() * 8.0;
            sparks.spawn(at.add(0, 0.8, 0))
                    .velocity(Math.cos(a) * side * speed, up * speed, Math.sin(a) * side * speed)
                    .life(0.6f + random().nextFloat() * 0.8f)
                    .size(0.035f + random().nextFloat() * 0.02f)
                    .delay(random().nextFloat() * 0.08f);
        }
        bursts.spawn(at.add(0, 1.0, 0)).life(0.75f).size(radius * 0.9f, radius * 1.3f)
                .rotation(random().nextFloat() * 6.283f, 0f);
        rings.spawn(at.add(0, 0.06, 0)).life(1.1f).size(radius * 2.6f);
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        PassOptions order = PassOptions.PARTICLES;
        out.particles(smoke, order, u -> u.texture("Noise", SMOKE_NOISE).f4("FireGlow", 1.0f, 0.35f, 0.08f, 1.6f));
        out.particles(heat, order, u -> u.texture("Noise", FBM));
        out.particles(embers, order, u -> u.texture("Ramp", RAMP));
        out.particles(rings, order, u -> u.texture("Ramp", RAMP).texture("Noise", FBM));
        out.particles(bursts, order, u -> u.texture("Book", BOOK).texture("Ramp", RAMP));
        out.particles(sparks, order, u -> u.texture("Ramp", RAMP));
    }

    @Override
    protected void screen(FrameContext frame, ScreenParams screen) {
        screen.bloom(0.8f * (1f - stopProgress(frame)), 1.2f);
    }

    private Vec3 disk(Vec3 at, double r, double y) {
        double a = random().nextDouble() * Math.PI * 2.0;
        double d = r * Math.sqrt(random().nextDouble());
        return at.add(Math.cos(a) * d, y, Math.sin(a) * d);
    }

    private double spread(double s) {
        return (random().nextDouble() * 2.0 - 1.0) * s;
    }

    private static ResourceLocation material(String name) {
        return BHFxMod.id(name);
    }

    private static ResourceLocation tex(String name) {
        return BHFxMod.id("textures/fx/" + name + ".png");
    }
}
