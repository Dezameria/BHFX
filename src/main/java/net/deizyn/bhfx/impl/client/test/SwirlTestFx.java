package net.deizyn.bhfx.impl.client.test;

import net.minecraft.resources.ResourceLocation;
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

/**
 * Built-in effect for /bhfx test (bhfx:test_swirl): tests ribbons drawn all at once along arcs, with one shared
 * texture stretched along them ({@code p.along}) and strips laid in planes ({@code ribbonNormal} / per-point
 * {@code normal}). An anime wind swirl, a gust every {@value #GUST_EVERY} ticks:
 * <ul>
 *   <li>three arcs flat on the ground (batch normal up), spinning and spreading outwards</li>
 *   <li>three tilted rings round the body (each point's own normal), spinning round the vertical axis, so they
 *       precess</li>
 *   <li>two curls that rise and tighten above, turned to the camera (no normal)</li>
 * </ul>
 * Material {@code bhfx:test_swirl} (noise_fbm streaks swept from tail to head). Params: {@code radius} (default 2.4).
 * Best on flat ground; look from the side and from above.
 */
public final class SwirlTestFx extends Fx {
    private static final FxTexture NOISE = FxTexture.of(BHFxMod.id("textures/fx/noise_fbm.png"));
    private static final ResourceLocation MATERIAL = BHFxMod.id("test_swirl");
    private static final int GUST_EVERY = 18;
    private static final int STOP_SPAWNING_BEFORE_END = 20;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private final float radius;
    private final FxParticles ground;
    private final FxParticles rings;
    private final FxParticles curls;
    private final FxParticles.Ribbon groundRibbon;
    private final FxParticles.Ribbon ringRibbon;
    private final FxParticles.Ribbon curlRibbon;
    private boolean gustDue;

    public SwirlTestFx(FxInit init) {
        super(init);
        radius = Math.max(0.6f, params().get(FxParam.RADIUS, 2.4f));
        ground = createParticles(MATERIAL, 768)
                .facing(FxParticles.Facing.RIBBON).blend(FxParticles.Blend.ALPHA)
                .ribbonNormal(0, 1, 0).softness(0.05f).drag(1.4f)
                .alphaOverLife(1f, 1f, 0f);
        rings = createParticles(MATERIAL, 512)
                .facing(FxParticles.Facing.RIBBON).blend(FxParticles.Blend.ALPHA)
                .softness(0.2f).turbulence(0.05f, 0.8f)
                .alphaOverLife(1f, 1f, 0f);
        curls = createParticles(MATERIAL, 256)
                .facing(FxParticles.Facing.RIBBON).blend(FxParticles.Blend.ALPHA)
                .softness(0.2f).gravity(0, 0.8, 0)
                .alphaOverLife(1f, 0.9f, 0f);
        groundRibbon = ground.ribbon();
        ringRibbon = rings.ribbon();
        curlRibbon = curls.ribbon();
        BHFxClient.preload(NOISE);
    }

    @Override
    protected int defaultDuration() {
        return 200;
    }

    @Override
    protected int endTicks(StopMode mode) {
        return mode == StopMode.IMMEDIATE ? 0 : 30;
    }

    @Override
    protected void tick(FxContext ctx, int age) {
        int d = duration();
        boolean spawning = !stopping() && (d < 0 || age < d - STOP_SPAWNING_BEFORE_END);
        if (spawning && age % GUST_EVERY == 0) {
            gustDue = true;
        }
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        Vec3 at = frame.position();
        // orbiting points keep their place relative to the centre at spawn: set it first, from the frame position
        ground.orbit(at, UP);
        rings.orbit(at, UP);
        curls.orbit(at, UP);
        if (gustDue) {
            gustDue = false;
            gust(at);
        }
        PassOptions order = PassOptions.PARTICLES;
        out.particles(ground, order, u -> u.texture("Noise", NOISE));
        out.particles(rings, order, u -> u.texture("Noise", NOISE));
        out.particles(curls, order, u -> u.texture("Noise", NOISE));
    }

    private void gust(Vec3 at) {
        double turn = random().nextDouble() * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            // flat on the ground (the batch normal), spinning and spreading out
            double r = radius * (0.45 + 0.28 * i);
            double start = turn + i * 2.1;
            double span = Math.PI * (1.15 + 0.35 * random().nextDouble());
            float spin = 4.2f - i * 0.6f;
            float spread = -1.4f - i * 0.4f;
            float w0 = 0.16f + 0.05f * i;
            float w1 = 0.32f + 0.08f * i;
            groundRibbon.cut();
            arc(groundRibbon, at.add(0, 0.06, 0), UP, r, start, span, 44, (s, f) -> s
                    .orbit(spin, -1.5f).pull(spread)
                    .life(1.05f).size(w0, w1)
                    .color(1.05f, 1.08f, 1.12f, 0.95f)
                    .data(3f, 0.25f, 0.65f, 0f));
        }
        for (int i = 0; i < 3; i++) {
            // tilted rings round the body: each point carries its ring's normal, which turns as the ring spins
            double tilt = Math.toRadians(22 + 16 * i + random().nextDouble() * 10);
            double phi = turn + i * 2.4;
            Vec3 n = new Vec3(Math.sin(tilt) * Math.cos(phi), Math.cos(tilt), Math.sin(tilt) * Math.sin(phi));
            double r = radius * (0.32 + 0.06 * i);
            Vec3 c = at.add(0, 0.6 + 0.45 * i, 0);
            double start = random().nextDouble() * Math.PI * 2;
            float spin = 6.5f - i;
            ringRibbon.cut();
            arc(ringRibbon, c, n, r, start, Math.PI * 1.45, 40, (s, f) -> s
                    .normal(n)
                    .orbit(spin, 0f)
                    .life(0.85f).size(0.09f, 0.14f)
                    .color(1.1f, 1.12f, 1.15f, 0.9f)
                    .data(2.5f, 0.4f, 0.75f, 0f));
        }
        for (int i = 0; i < 2; i++) {
            // curls above: a rising, tightening spiral turned to the camera (no normal)
            double a0 = turn + Math.PI * i;
            double r0 = radius * 0.42;
            Vec3 base = at.add(0, 1.5, 0);
            curlRibbon.cut();
            int n = 32;
            for (int k = 0; k < n; k++) {
                float f = k / (float) (n - 1);
                double a = a0 + f * Math.PI * 1.7;
                double r = r0 * (1.0 - 0.65 * f);
                curlRibbon.spawn(base.x + Math.cos(a) * r, base.y + f * 0.7, base.z - Math.sin(a) * r)
                        .along(f)
                        .orbit(3.0f, 0f)
                        .life(0.95f).size(0.07f, 0.1f)
                        .color(1.1f, 1.12f, 1.15f, 0.8f)
                        .data(2f, 0.5f, 0.8f, 0f);
            }
        }
    }

    /** Sets the rest of one arc point; {@code fraction} is 0 at the tail, 1 at the head. */
    private interface ArcPoint {
        void accept(FxParticles.ParticleSpawn spawn, float fraction);
    }

    /**
     * One ribbon along an arc of a circle round {@code centre} in the plane with unit normal {@code n}, from
     * {@code start} through {@code span} radians in the positive turning direction round {@code n} (the way a
     * positive orbit turns, so the head leads), with {@code along} 0..1 from tail to head.
     */
    private static void arc(FxParticles.Ribbon ribbon, Vec3 centre, Vec3 n, double r, double start, double span,
                            int points, ArcPoint each) {
        Vec3 nn = n.normalize();
        Vec3 e1 = Math.abs(nn.y) < 0.9 ? UP.cross(nn).normalize() : new Vec3(1, 0, 0).cross(nn).normalize();
        Vec3 e2 = nn.cross(e1);
        for (int k = 0; k < points; k++) {
            float f = k / (float) (points - 1);
            double a = start + f * span;
            double c = Math.cos(a) * r;
            double s = Math.sin(a) * r;
            FxParticles.ParticleSpawn spawn = ribbon.spawn(
                    centre.x + e1.x * c + e2.x * s,
                    centre.y + e1.y * c + e2.y * s,
                    centre.z + e1.z * c + e2.z * s).along(f);
            each.accept(spawn, f);
        }
    }
}
