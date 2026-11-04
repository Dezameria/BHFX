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
import net.deizyn.bhfx.api.client.FxEmitter;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.FxTexture;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;
import net.deizyn.bhfx.api.client.ScreenParams;

/**
 * Built-in effect for /bhfx test (bhfx:test_vortex): tests emitter shapes, streams and orbit motion, reusing the
 * test_particles materials.
 * <ul>
 *   <li>whirlwind round the effect — dust (ALPHA) and embers streamed from a disc / cylinder, orbiting the batch axis
 *       (kept on the effect each frame, so it follows when attached), rising and widening ({@code pull} &lt; 0)</li>
 *   <li>a black hole above, its axis tilted — every 2.5 s an even {@code ring} of streaks across that axis
 *       (VELOCITY facing, so the streaks show the turning velocity) spirals in with {@code pull} and spin-up</li>
 *   <li>a comet circling the whirlwind, its trail a stream emitted per frame along the moving point</li>
 *   <li>each pulse also fires an even {@code ring} of embers outwards and a {@code cone} fountain of falling sparks</li>
 * </ul>
 * Params: {@code radius} (default 2.5).
 */
public final class VortexTestFx extends Fx {
    private static final FxTexture RAMP = FxTexture.of(tex("ramp_fire")).clamp();
    private static final FxTexture SMOKE_NOISE = FxTexture.packed(tex("noise_fbm"), tex("noise_cells"), null, null);
    private static final FxEmitter.Shape POINT = FxEmitter.point();
    private static final FxEmitter.Shape FOUNTAIN = FxEmitter.cone(0.35);
    /** Tilted so the black hole's spin plane shows in depth. */
    private static final Vec3 HOLE_AXIS = new Vec3(0.45, 1.0, 0.25);
    private static final int PULSE_EVERY = 50;
    private static final int STOP_SPAWNING_BEFORE_END = 40;

    private final float radius;
    private final FxEmitter.Shape dustDisc;
    private final FxEmitter.Shape emberWall;
    private final FxEmitter.Shape holeRing;
    private final FxEmitter.Shape burstRing;
    private final FxParticles dust;
    private final FxParticles embers;
    private final FxParticles streaks;
    private final FxParticles trail;
    private final FxParticles sparks;
    private final FxParticles.Stream dustFlow;
    private final FxParticles.Stream emberFlow;
    private final FxParticles.Stream trailFlow;
    private boolean spawning = true;

    public VortexTestFx(FxInit init) {
        super(init);
        radius = Math.max(0.5f, params().get(FxParam.RADIUS, 2.5f));
        dustDisc = FxEmitter.disc(radius * 0.7);
        emberWall = FxEmitter.cylinder(radius * 0.45, 0.6);
        holeRing = FxEmitter.ring(radius * 1.3).axis(HOLE_AXIS).even();
        burstRing = FxEmitter.ring(radius * 0.3).even();
        dust = createParticles(material("test_smoke"), 768)
                .blend(FxParticles.Blend.ALPHA).drag(0.4f).softness(0.8f);
        embers = createParticles(material("test_ember"), 2048).drag(0.3f).softness(0.3f);
        streaks = createParticles(material("test_spark"), 1024)
                .facing(FxParticles.Facing.VELOCITY).stretch(0.05f).softness(0.1f);
        trail = createParticles(material("test_ember"), 1024).drag(1.5f).softness(0.2f);
        sparks = createParticles(material("test_spark"), 512)
                .facing(FxParticles.Facing.VELOCITY).stretch(0.06f).gravity(0, -12, 0).drag(0.6f).softness(0.1f);
        dustFlow = dust.stream(40f);
        emberFlow = embers.stream(90f);
        trailFlow = trail.stream(150f);
        BHFxClient.preload(RAMP);
        BHFxClient.preload(SMOKE_NOISE);
    }

    @Override
    protected int defaultDuration() {
        return 240;
    }

    @Override
    protected int endTicks(StopMode mode) {
        return mode == StopMode.IMMEDIATE ? 0 : 60;
    }

    private Vec3 hole(Vec3 at) {
        return at.add(0, radius + 2.0, 0);
    }

    @Override
    protected void tick(FxContext ctx, int age) {
        int d = duration();
        spawning = !stopping() && (d < 0 || age < d - STOP_SPAWNING_BEFORE_END);
        if (!spawning) {
            return;
        }
        Vec3 at = ctx.position();
        // the whirlwind: streams from shapes, every particle orbiting the batch axis (set in passes)
        dustFlow.emit(dustDisc, at.x, at.y + 0.2, at.z, 0.4, (s, p) -> s
                .velocity(p.dx() * 0.4, 1.2 + random().nextDouble() * 0.8, p.dz() * 0.4)
                .orbit(2.2f + random().nextFloat(), 0.4f)
                .pull(-0.35f)
                .life(2.6f + random().nextFloat())
                .size(0.35f, 1.1f)
                .rotation(random().nextFloat() * 6.283f, 0.6f)
                .color(0x6B5A48, 0.55f));
        emberFlow.emit(emberWall, at, 0.2, (s, p) -> s
                .velocity(p.dx() * 0.2, 2.0 + random().nextDouble() * 1.5, p.dz() * 0.2)
                .orbit(4.5f + random().nextFloat() * 2f, 0f)
                .pull(-0.25f)
                .life(1.4f + random().nextFloat())
                .size(0.06f, 0.02f));
        if (age >= 10 && (age - 10) % PULSE_EVERY == 0) {
            pulse(ctx, at);
        }
    }

    private void pulse(FxContext ctx, Vec3 at) {
        ctx.sound(SoundEvents.ENDER_EYE_DEATH, at, 1.2f, 0.6f + random().nextFloat() * 0.2f);
        Vec3 hole = hole(at);
        streaks.orbit(hole, HOLE_AXIS);
        // an even ring of streaks spiralling into the hole, turning faster as they fall in
        streaks.emit(holeRing, hole, 160, 0, (s, p) -> s
                .pull(radius * 0.9f)
                .orbit(1.2f, 3.5f)
                .life(1.5f + random().nextFloat() * 0.2f)
                .size(0.03f + random().nextFloat() * 0.015f)
                .color(0.7f, 0.45f, 1.0f, 1f)
                .delay(p.fraction() * 0.15f));
        // an even ring of embers thrown outwards, and a fountain of falling sparks from the top of the whirlwind
        embers.emit(burstRing, at.x, at.y + 0.3, at.z, 64, 7.0, (s, p) -> s
                .life(0.9f)
                .size(0.09f, 0.02f));
        sparks.emit(FOUNTAIN, at.x, at.y + 2.5, at.z, 80, 9.0, (s, p) -> s
                .life(1.2f + random().nextFloat() * 0.6f)
                .size(0.035f)
                .delay(random().nextFloat() * 0.1f));
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        Vec3 at = frame.position();
        // the whirlwind's axis follows the effect between ticks
        dust.orbit(at, new Vec3(0, 1, 0));
        embers.orbit(at, new Vec3(0, 1, 0));
        streaks.orbit(hole(at), HOLE_AXIS); // the hole above follows too
        if (spawning) {
            // the comet: a point moving every frame, its trail streamed per frame along the way
            float t = seconds(frame);
            double a = t * 2.4;
            double r = radius * 1.4;
            trailFlow.emit(POINT, at.x + Math.cos(a) * r, at.y + 1.4 + Math.sin(t * 1.7) * 0.6, at.z + Math.sin(a) * r,
                    0.4, (s, p) -> s.life(0.7f).size(0.09f, 0.01f).color(0.6f, 0.85f, 1.4f, 1f));
        }
        PassOptions order = PassOptions.PARTICLES;
        out.particles(dust, order, u -> u.texture("Noise", SMOKE_NOISE).f4("FireGlow", 1.0f, 0.5f, 0.2f, 0.4f));
        out.particles(embers, order, u -> u.texture("Ramp", RAMP));
        out.particles(trail, order, u -> u.texture("Ramp", RAMP));
        out.particles(streaks, order, u -> u.texture("Ramp", RAMP));
        out.particles(sparks, order, u -> u.texture("Ramp", RAMP));
    }

    @Override
    protected void screen(FrameContext frame, ScreenParams screen) {
        screen.bloom(0.7f * (1f - stopProgress(frame)), 1.2f);
    }

    private static ResourceLocation material(String name) {
        return BHFxMod.id(name);
    }

    private static ResourceLocation tex(String name) {
        return BHFxMod.id("textures/fx/" + name + ".png");
    }
}
