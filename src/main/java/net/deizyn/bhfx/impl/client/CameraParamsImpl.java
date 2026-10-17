package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.api.client.CameraParams;
import net.deizyn.bhfx.impl.BHFxConfig;
import net.minecraft.util.Mth;

/**
 * Collects every effect's camera requests during a frame; the camera events of the next frame read the result.
 * Gating happens here, per effect, so an effect only ever sees what it may change.
 */
final class CameraParamsImpl implements CameraParams {
    private Level allowed = Level.NONE;
    private float fov = 1f;

    void reset() {
        fov = 1f;
    }

    /** Bind to one effect: what it may change depends on whose spell it is and the player's settings. */
    CameraParamsImpl bindFor(boolean localCaster) {
        allowed = localCaster || BHFxConfig.CAMERA_FROM_OTHERS.get() ? BHFxConfig.CAMERA_CONTROL.get() : Level.NONE;
        return this;
    }

    @Override
    public Level allowed() {
        return allowed;
    }

    @Override
    public CameraParams fov(float multiplier) {
        if (allowed == Level.SUBTLE && Float.isFinite(multiplier)) {
            float m = Mth.clamp(multiplier, 0.7f, 1.3f);
            if (Math.abs(m - 1f) > Math.abs(fov - 1f)) {
                fov = m;
            }
        }
        return this;
    }

    /** FOV multiplier after the player's fovScale. */
    float fovMultiplier() {
        return 1f + (fov - 1f) * BHFxConfig.FOV_SCALE.get().floatValue();
    }

    boolean idle() {
        return fov == 1f;
    }
}
