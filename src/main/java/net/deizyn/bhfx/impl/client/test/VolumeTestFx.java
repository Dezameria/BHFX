package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.FxParam;
import net.deizyn.bhfx.api.client.BHFxClient;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxTexture;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;

/**
 * Built-in effect for /bhfx test and /bhfx_play: a ball of glowing mist, 40 march steps per pixel
 * ({@code test_volume.fsh}). {@code bhfx:test_volume} draws it as a half-resolution layer
 * ({@link PassOptions#halfRes}); {@code bhfx:test_volume_full} draws the same shader as an ordinary pass, to compare
 * the look and the GPU time. The ball sits half in the ground, so the depth-aware upsample shows along the grass.
 */
public final class VolumeTestFx extends Fx {
    private static final FxTexture NOISE = FxTexture.of(BHFxMod.id("textures/fx/noise_fbm.png"));

    private final boolean half;
    private final float radius;

    public VolumeTestFx(FxInit init, boolean half) {
        super(init);
        this.half = half;
        this.radius = params().get(FxParam.RADIUS, 3.5f);
        BHFxClient.preload(NOISE);
    }

    @Override
    protected int defaultDuration() {
        return 400;
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        float t = seconds(frame);
        Vec3 c = frame.position().add(0, radius * 0.45, 0);
        Vec3 rel = frame.rel(c);
        float fade = Math.min(1f, t / 0.6f) * (1f - stopProgress(frame));
        PassOptions options = PassOptions.DEFAULT.within(c, radius + 0.5);
        if (half) {
            options = options.halfRes();
        }
        out.add(BHFxMod.id("test_volume"), options, u -> u
                .texture("Noise", NOISE)
                .f4("Bound", (float) rel.x, (float) rel.y, (float) rel.z, radius)
                .f4("P0", t, fade, 0f, 0f)
                .color("Tint", 0x6040FF, 1f)
                .f4("Mode", half ? 1f : 0f, 0f, 0f, 0f));
        out.light(c, 0x6040FF, 0.9f * fade, radius * 2.2f);
    }
}
