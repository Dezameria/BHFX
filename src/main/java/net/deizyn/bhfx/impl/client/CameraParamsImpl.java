package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.api.client.CameraParams;
import net.deizyn.bhfx.api.client.CameraPose;
import net.deizyn.bhfx.impl.BHFxConfig;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Collects every effect's camera requests during a frame; the camera events of the next frame read the result.
 * Gating happens here, per effect, so an effect only ever sees what it may change.
 */
final class CameraParamsImpl implements CameraParams {
    private static final float MAX_ROLL = 30f;

    private Level allowed = Level.NONE;

    private float fov = 1f;
    private float roll;
    private boolean hideHand;
    private boolean hideHud;
    @Nullable
    private CameraPose pose;
    private float poseWeight;

    void reset() {
        fov = 1f;
        roll = 0f;
        hideHand = false;
        hideHud = false;
        pose = null;
        poseWeight = 0f;
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
        if (allowed.ordinal() >= Level.SUBTLE.ordinal() && Float.isFinite(multiplier)) {
            float m = Mth.clamp(multiplier, 0.7f, 1.3f);
            if (Math.abs(m - 1f) > Math.abs(fov - 1f)) {
                fov = m;
            }
        }
        return this;
    }

    @Override
    public CameraParams hideHand() {
        if (allowed.ordinal() >= Level.SUBTLE.ordinal()) {
            hideHand = true;
        }
        return this;
    }

    @Override
    public CameraParams roll(float degrees) {
        if (allowed == Level.FULL && Float.isFinite(degrees)) {
            roll = Mth.clamp(roll + degrees, -MAX_ROLL, MAX_ROLL);
        }
        return this;
    }

    @Override
    public CameraParams hideHud() {
        if (allowed == Level.FULL) {
            hideHud = true;
        }
        return this;
    }

    @Override
    public CameraParams override(CameraPose pose, float weight) {
        if (allowed == Level.FULL && pose != null && weight > poseWeight && Float.isFinite(weight)) {
            this.pose = pose;
            this.poseWeight = Math.min(weight, 1f);
        }
        return this;
    }

    // ------------------------------------------------------------------ read by the camera events

    /** FOV multiplier after the player's fovScale. */
    float fovMultiplier() {
        return 1f + (fov - 1f) * BHFxConfig.FOV_SCALE.get().floatValue();
    }

    float rollDegrees() {
        return roll;
    }

    boolean hideHandRequested() {
        return hideHand;
    }

    boolean hideHudRequested() {
        return hideHud;
    }

    @Nullable
    CameraPose pose() {
        return pose;
    }

    float poseWeight() {
        return poseWeight;
    }

    boolean idle() {
        return fov == 1f && roll == 0f && !hideHand && !hideHud && pose == null;
    }
}
