package net.deizyn.bhfx.impl.client;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.ImpactStyle;
import net.deizyn.bhfx.api.client.ScreenParams;
import net.deizyn.bhfx.impl.BHFxConfig;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.Optional;

/** Merged full-screen parameters for one frame. Comfort scaling is applied on read. */
public final class ScreenParamsImpl implements ScreenParams {
    public static final int MAX_RIPPLES = 8;

    private final FrameContextImpl frame;
    private float shake;
    private float flash;
    private int flashRgb;
    private float vignette;
    private int vignetteRgb;
    private float desaturate;
    private float aberration;
    private float zoomBlur;
    private float bloom;
    private float bloomRadius;
    private float impact;
    @Nullable
    private ImpactStyle impactStyle;
    private float impactU = 0.5f;
    private float impactV = 0.5f;
    private float speedLines;
    private float contrast;
    private float grain;
    private boolean freeze;
    private float afterimage;
    private float stepFps;
    private float synthetic;
    private final float[] rippleA = new float[MAX_RIPPLES * 4];
    private final float[] rippleB = new float[MAX_RIPPLES * 4];
    private int ripples;

    ScreenParamsImpl(FrameContextImpl frame) {
        this.frame = frame;
    }

    void reset() {
        shake = flash = vignette = desaturate = aberration = zoomBlur = bloom = bloomRadius = 0f;
        impact = speedLines = contrast = grain = afterimage = stepFps = synthetic = 0f;
        flashRgb = vignetteRgb = 0;
        impactStyle = null;
        impactU = impactV = 0.5f;
        freeze = false;
        ripples = 0;
        java.util.Arrays.fill(rippleA, 0f);
        java.util.Arrays.fill(rippleB, 0f);
    }

    /** True when the image needs no post pass (shake moves the camera, bloom needs passes to have drawn light). */
    boolean imageIdle() {
        return flash < 1e-4f && vignette < 1e-4f && desaturate < 1e-4f && aberration < 1e-4f && zoomBlur < 1e-4f
                && impact < 1e-4f && speedLines < 1e-4f && contrast < 1e-4f && grain < 1e-4f && !freeze
                && afterimage < 1e-4f && stepFps < 0.5f && ripples == 0;
    }

    // ------------------------------------------------------------------ ScreenParams

    @Override
    public ScreenParams shake(float amount) {
        shake = Math.max(shake, amount);
        return this;
    }

    @Override
    public ScreenParams flash(int rgb, float strength) {
        if (strength > flash) {
            flash = strength;
            flashRgb = rgb;
        }
        return this;
    }

    @Override
    public ScreenParams vignette(int rgb, float strength) {
        if (strength > vignette) {
            vignette = strength;
            vignetteRgb = rgb;
        }
        return this;
    }

    @Override
    public ScreenParams desaturate(float amount) {
        desaturate = Math.max(desaturate, amount);
        return this;
    }

    @Override
    public ScreenParams aberration(float amount) {
        aberration = Math.max(aberration, amount);
        return this;
    }

    @Override
    public ScreenParams zoomBlur(float amount) {
        zoomBlur = Math.max(zoomBlur, amount);
        return this;
    }

    @Override
    public ScreenParams bloom(float strength, float radius) {
        bloom = Math.max(bloom, strength);
        bloomRadius = Math.max(bloomRadius, radius);
        return this;
    }

    @Override
    public ScreenParams impact(ImpactStyle style, float strength, Vec3 focus) {
        if (strength <= impact || !BHFxConfig.IMPACT_FRAMES.get()) {
            return this;
        }
        impact = Math.min(strength, 1f);
        impactStyle = style;
        Optional<Vector2f> uv = frame.screenUv(focus);
        impactU = uv.map(v -> clamp01(v.x)).orElse(0.5f);
        impactV = uv.map(v -> clamp01(v.y)).orElse(0.5f);
        return this;
    }

    @Override
    public ScreenParams speedLines(float amount) {
        speedLines = Math.max(speedLines, amount);
        return this;
    }

    @Override
    public ScreenParams contrast(float amount) {
        contrast = Math.max(contrast, amount);
        return this;
    }

    @Override
    public ScreenParams grain(float amount) {
        grain = Math.max(grain, amount);
        return this;
    }

    @Override
    public ScreenParams freeze() {
        freeze = true;
        return this;
    }

    @Override
    public ScreenParams afterimage(float amount) {
        afterimage = Math.max(afterimage, amount);
        return this;
    }

    @Override
    public ScreenParams stepFps(float fps) {
        if (fps > 0.5f) {
            stepFps = stepFps > 0.5f ? Math.min(stepFps, fps) : fps;
        }
        return this;
    }

    @Override
    public ScreenParams synthetic(float coverage) {
        synthetic = Math.max(synthetic, coverage);
        return this;
    }

    @Override
    public ScreenParams ripple(Vec3 center, float radius, float width, float strength) {
        if (ripples >= MAX_RIPPLES || strength <= 1e-4f) {
            return this;
        }
        Optional<Vector2f> uv = frame.screenUv(center);
        if (uv.isEmpty()) {
            return this;
        }
        // blocks at that distance → screen units (screen height = 1)
        double perBlock = frame.projScaleY() * 0.5 / Math.max(0.5, frame.cameraPos().distanceTo(center));
        int i = ripples++ * 4;
        rippleA[i] = uv.get().x;
        rippleA[i + 1] = uv.get().y;
        rippleA[i + 2] = (float) (radius * perBlock);
        rippleA[i + 3] = Math.min(strength, 1f);
        rippleB[i] = (float) Math.max(width * perBlock, 0.002);
        return this;
    }

    private static float clamp01(float x) {
        return x < 0f ? 0f : Math.min(x, 1f);
    }

    // ------------------------------------------------------------------ read by the renderer

    float shakeValue() { return shake; }
    public float flashStrength() { return (float) (flash * BHFxConfig.FLASH_SCALE.get()); }
    public int flashRgb() { return flashRgb; }
    public float vignetteStrength() { return vignette; }
    public int vignetteRgb() { return vignetteRgb; }
    public float desaturateValue() { return desaturate; }
    public float aberrationValue() { return aberration; }
    public float zoomBlurValue() { return zoomBlur; }
    public float bloomStrength() { return bloom; }
    public float bloomRadius() { return Math.max(1f, bloomRadius); }
    public float impactStrength() { return impactStyle == null ? 0f : impact; }
    @Nullable public ImpactStyle impactStyle() { return impactStyle; }
    public float impactU() { return impactU; }
    public float impactV() { return impactV; }
    public float speedLinesValue() { return speedLines; }
    public float contrastValue() { return contrast; }
    public float grainValue() { return grain; }
    public boolean freezeRequested() { return freeze; }
    public float afterimageValue() { return afterimage; }
    public float stepFpsValue() { return stepFps; }
    public float syntheticValue() { return synthetic; }
    public int rippleCount() { return ripples; }
    public float[] rippleA() { return rippleA; }
    public float[] rippleB() { return rippleB; }
}
