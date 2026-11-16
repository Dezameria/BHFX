package net.deizyn.bhfx.impl.client.test;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxContext;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.ScreenParams;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_wave): one big blast at the target, to check travelling
 * waves. The flash is at once, wherever you stand; a ring bends the image as the shock front runs out at the speed of
 * sound (343 blocks a second); the boom and the camera shake arrive when that front reaches you
 * ({@link FxContext#soundWave}, {@link FxContext#shakeWave}): a sharp explosion within 64 blocks, thunder within 256,
 * low thunder further off. Stand 30, 150 and 400 blocks away: the gap between flash and boom grows to about 0.1, 0.45
 * and 1.2 seconds, and the ring passes you as the boom lands.
 */
public final class WaveTestFx extends Fx {
    private static final float RING_SECONDS = 4f;

    public WaveTestFx(FxInit init) {
        super(init);
    }

    @Override
    protected int defaultDuration() {
        return (int) (RING_SECONDS * 20f) + 10;
    }

    @Override
    protected double cullDistance() {
        return 1600.0;
    }

    @Override
    protected void tick(FxContext ctx, int age) {
        if (age != 0) {
            return;
        }
        Vec3 at = init().target();
        ctx.soundWave(SoundEvents.GENERIC_EXPLODE, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundEvents.LIGHTNING_BOLT_THUNDER,
                64.0, 256.0, at, 12f, 0.8f);
        ctx.shakeWave(at, 0.9f, 600.0);
    }

    @Override
    protected void screen(FrameContext frame, ScreenParams screen) {
        float since = seconds(frame);
        Vec3 at = init().target();
        if (since < 0.25f) {
            screen.flash(0xFFF2DD, 0.8f * (1f - since / 0.25f));
        }
        if (since < RING_SECONDS) {
            float front = since * FxContext.SPEED_OF_SOUND;
            screen.ripple(at, front, 4f + since * 6f, 0.8f * (1f - since / RING_SECONDS));
        }
    }
}
