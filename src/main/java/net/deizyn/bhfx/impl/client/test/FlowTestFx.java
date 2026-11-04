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
 * Built-in effect for /bhfx test (bhfx:test_flow): tests turbulence, over-life curves, ribbons, ground-snapped
 * emitters and the built-in materials (no material of its own). Best on uneven ground.
 * <ul>
 *   <li>smoke ({@code DOT}, ALPHA) and embers ({@code DOT}, ADD) drifting in turbulence, coloured, faded and grown by
 *       over-life keys; the embers by a ramp texture played backwards</li>
 *   <li>three helix ribbons ({@code RIBBON}): fixed emitters whose points rise and orbit, so each ribbon winds up</li>
 *   <li>a comet whose trail is a ribbon streamed per frame, wobbling in turbulence, coloured by the ramp</li>
 *   <li>every 2.5 s: an even dust ring and a ring of flipbook scorch marks ({@code SPRITE}, FLAT) snapped onto the
 *       terrain ({@code onGround}), a flipbook puff and a cone of {@code STREAK} sparks</li>
 * </ul>
 * Params: {@code radius} (default 3).
 */
public final class FlowTestFx extends Fx {
    private static final FxTexture RAMP = FxTexture.of(tex("ramp_fire")).clamp();
    private static final FxTexture BOOK = FxTexture.of(tex("burst_flipbook")).clamp();
    private static final FxEmitter.Shape POINT = FxEmitter.point();
    private static final FxEmitter.Shape SPARK_CONE = FxEmitter.cone(0.5);
    private static final int PULSE_EVERY = 50;
    private static final int STOP_SPAWNING_BEFORE_END = 40;
    private static final int HELICES = 3;

    private final float radius;
    private final FxEmitter.Shape smokeDisc;
    private final FxEmitter.Shape emberDisc;
    private final FxEmitter.Shape dustRing;
    private final FxEmitter.Shape scorchRing;
    private final FxParticles smoke;
    private final FxParticles embers;
    private final FxParticles helix;
    private final FxParticles trail;
    private final FxParticles sparks;
    private final FxParticles dust;
    private final FxParticles scorch;
    private final FxParticles puff;
    private final FxParticles.Stream smokeFlow;
    private final FxParticles.Stream emberFlow;
    private final FxParticles.Stream[] helixFlows = new FxParticles.Stream[HELICES];
    private final FxParticles.Stream trailFlow;
    private boolean spawning = true;

    public FlowTestFx(FxInit init) {
        super(init);
        radius = Math.max(0.5f, params().get(FxParam.RADIUS, 3f));
        smokeDisc = FxEmitter.disc(radius * 0.25);
        emberDisc = FxEmitter.disc(radius * 0.2);
        dustRing = FxEmitter.ring(radius * 0.6).even().onGround(4, 0.2);
        scorchRing = FxEmitter.ring(radius * 1.2).even().onGround(4, 0.03);

        smoke = createParticles(FxParticles.DOT, 512)
                .blend(FxParticles.Blend.ALPHA).gravity(0, 0.5, 0).drag(0.6f).softness(0.8f)
                .turbulence(0.7f, 0.3f, 0.3f)
                .colorOverLife(0x9A948C, 0x55504A, 0x242222)
                .alphaOverLife(0f, 0.65f, 0.45f, 0f)
                .sizeOverLife(0.5f, 1.2f, 1.8f, 2.4f);
        embers = createParticles(FxParticles.DOT, 1024)
                .drag(0.5f).softness(0.3f)
                .turbulence(0.5f, 0.8f, 0.5f)
                .colorOverLife(RAMP, 1f, 0.15f)
                .alphaOverLife(1f, 1f, 0f)
                .sizeOverLife(1f, 0.4f);
        helix = createParticles(FxParticles.RIBBON, 1024)
                .facing(FxParticles.Facing.RIBBON).softness(0.2f)
                .turbulence(0.12f, 0.6f)
                .colorOverLife(0x90F4FF, 0x3070FF, 0x7020C0)
                .alphaOverLife(1f, 1f, 0.6f, 0f)
                .sizeOverLife(0.6f, 1f, 0.3f);
        trail = createParticles(FxParticles.RIBBON, 512)
                .facing(FxParticles.Facing.RIBBON).softness(0.2f)
                .turbulence(0.25f, 1.2f)
                .colorOverLife(RAMP, 1f, 0f)
                .alphaOverLife(1f, 0.8f, 0f)
                .sizeOverLife(1f, 0.2f);
        sparks = createParticles(FxParticles.STREAK, 512)
                .facing(FxParticles.Facing.VELOCITY).stretch(0.05f).gravity(0, -12, 0).drag(0.5f).softness(0.1f)
                .colorOverLife(0xFFFFFF, 0xFFB040, 0xC02000);
        dust = createParticles(FxParticles.DOT, 256)
                .blend(FxParticles.Blend.ALPHA).drag(2.5f).softness(0.6f)
                .colorOverLife(0x8A7458, 0x5A4C3C)
                .alphaOverLife(0f, 0.6f, 0f)
                .sizeOverLife(0.6f, 1.5f, 2f);
        scorch = createParticles(FxParticles.SPRITE, 64)
                .facing(FxParticles.Facing.FLAT).softness(0f)
                .colorOverLife(0xFFD080, 0xFF6010, 0x601000);
        puff = createParticles(FxParticles.SPRITE, 16).softness(0.6f)
                .colorOverLife(RAMP, 1f, 0.3f);
        smokeFlow = smoke.stream(25f);
        emberFlow = embers.stream(60f);
        for (int i = 0; i < HELICES; i++) {
            helixFlows[i] = helix.stream(70f);
        }
        trailFlow = trail.stream(120f);
        BHFxClient.preload(RAMP);
        BHFxClient.preload(BOOK);
    }

    @Override
    protected int defaultDuration() {
        return 240;
    }

    @Override
    protected int endTicks(StopMode mode) {
        return mode == StopMode.IMMEDIATE ? 0 : 80;
    }

    @Override
    protected void tick(FxContext ctx, int age) {
        int d = duration();
        spawning = !stopping() && (d < 0 || age < d - STOP_SPAWNING_BEFORE_END);
        if (!spawning) {
            return;
        }
        Vec3 at = ctx.position();
        smokeFlow.emit(smokeDisc, at.x, at.y + 0.3, at.z, 0.2, (s, p) -> s
                .velocity(p.dx() * 0.2, 0.8 + random().nextDouble() * 0.4, p.dz() * 0.2)
                .life(3.5f + random().nextFloat())
                .size(0.5f)
                .rotation(random().nextFloat() * 6.283f, 0.4f));
        emberFlow.emit(emberDisc, at.x, at.y + 0.3, at.z, 0.3, (s, p) -> s
                .velocity(p.dx() * 0.3, 2.2 + random().nextDouble() * 1.2, p.dz() * 0.3)
                .life(1.6f + random().nextFloat())
                .size(0.07f)
                .color(2.5f, 2.5f, 2.5f, 1f));
        if (age >= 10 && (age - 10) % PULSE_EVERY == 0) {
            pulse(ctx, at);
        }
    }

    private void pulse(FxContext ctx, Vec3 at) {
        ctx.sound(SoundEvents.GENERIC_EXPLODE, at, 0.8f, 1.3f + random().nextFloat() * 0.2f);
        ctx.shake(at, 0.2f, 20.0);
        // on the terrain, whatever its shape
        dust.emit(dustRing, at, 48, 3.5, (s, p) -> s.life(1.6f).size(0.5f).rotation(random().nextFloat() * 6.283f, 0f)
                .color(1f, 1f, 1f, 1f));
        scorch.emit(scorchRing, at, 14, 0, (s, p) -> s.life(1.4f).size(0.7f)
                .rotation(random().nextFloat() * 6.283f, 0f).color(2f, 2f, 2f, 1f));
        puff.spawn(at.add(0, 1.0, 0)).life(0.8f).size(radius * 0.5f, radius * 0.8f)
                .rotation(random().nextFloat() * 6.283f, 0f).color(2.5f, 2.5f, 2.5f, 1f);
        sparks.emit(SPARK_CONE, at.x, at.y + 1.0, at.z, 70, 10.0, (s, p) -> s
                .life(0.8f + random().nextFloat() * 0.6f)
                .size(0.03f)
                .color(3f, 3f, 3f, 1f)
                .delay(random().nextFloat() * 0.06f));
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        Vec3 at = frame.position();
        helix.orbit(at, new Vec3(0, 1, 0));
        if (spawning) {
            // fixed emitters round the centre; their points rise and orbit, so each ribbon winds into a helix. Fed per
            // frame from the same position as the orbit centre: from tick() the ribbon's newest end would jump each
            // tick, and a moving caster would kink it (the tick position runs ahead of the frame's centre)
            for (int i = 0; i < HELICES; i++) {
                double a = i * Math.PI * 2 / HELICES;
                double r = radius * 0.55;
                helixFlows[i].emit(POINT, at.x + Math.cos(a) * r, at.y + 0.2, at.z + Math.sin(a) * r, 0, (s, p) -> s
                        .velocity(0, 2.0, 0)
                        .orbit(2.4f, 0f)
                        .life(1.8f)
                        .size(0.14f)
                        .color(1.6f, 1.6f, 1.6f, 1f));
            }
            // the comet: a point moved every frame; its ribbon trail is streamed per frame along the way
            float t = seconds(frame);
            double a = t * 1.9;
            double r = radius * 1.5;
            trailFlow.emit(POINT, at.x + Math.cos(a) * r, at.y + 1.8 + Math.sin(t * 2.3) * 0.8,
                    at.z + Math.sin(a * 1.3) * r, 0, (s, p) -> s.life(0.9f).size(0.2f).color(2.2f, 2.2f, 2.2f, 1f));
        }
        PassOptions order = PassOptions.PARTICLES;
        out.particles(scorch, order, u -> u.texture("Sprite", BOOK).f4("SpriteGrid", 4f, 4f, 16f, 1f));
        out.particles(dust);
        out.particles(smoke);
        out.particles(embers);
        out.particles(helix);
        out.particles(trail);
        out.particles(puff, order, u -> u.texture("Sprite", BOOK).f4("SpriteGrid", 4f, 4f, 16f, 1f));
        out.particles(sparks);
    }

    @Override
    protected void screen(FrameContext frame, ScreenParams screen) {
        screen.bloom(0.7f * (1f - stopProgress(frame)), 1.2f);
    }

    private static ResourceLocation tex(String name) {
        return BHFxMod.id("textures/fx/" + name + ".png");
    }
}
