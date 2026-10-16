package net.offkung.bhfx.impl.client;

import net.offkung.bhfx.api.client.ScreenParams;

/** Merged full-screen parameters for one frame. Strongest value wins; comfort scaling is applied on read. */
public final class ScreenParamsImpl implements ScreenParams {
    private float shake;
    private float flash;
    private int flashRgb;
    private float vignette;
    private int vignetteRgb;
    private float desaturate;
    private float aberration;

    void reset() {
        shake = flash = vignette = desaturate = aberration = 0f;
        flashRgb = vignetteRgb = 0;
    }

    boolean idle() {
        return shake < 1e-4f && flash < 1e-4f && vignette < 1e-4f && desaturate < 1e-4f && aberration < 1e-4f;
    }

    /** True when the composite pass has nothing to do (shake is applied to the camera, not the image). */
    public boolean imageIdle() {
        return flash < 1e-4f && vignette < 1e-4f && desaturate < 1e-4f && aberration < 1e-4f;
    }

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

    float shakeValue() { return shake; }
    public float flashStrength(double scale) { return (float) (flash * scale); }
    public int flashRgb() { return flashRgb; }
    public float vignetteStrength() { return vignette; }
    public int vignetteRgb() { return vignetteRgb; }
    public float desaturateValue() { return desaturate; }
    public float aberrationValue() { return aberration; }
}
