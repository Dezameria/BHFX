package net.deizyn.bhfx.api;

import org.jetbrains.annotations.ApiStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * A short full-screen reaction sent from the server without writing an Fx class: shake, flash, vignette, colour
 * fringes, zoom blur, desaturation. Each part fades out over its own number of ticks and gets weaker with the
 * viewer's distance from the pulse. The players' comfort settings (flashScale, shakeScale) apply as usual.
 *
 * <pre>{@code
 * BHFx.pulse(level, target, 24, ScreenPulse.of().shake(0.6f).flash(0xFFE0B0, 0.5f, 6));
 * }</pre>
 */
public final class ScreenPulse {
    private float shake;
    private int flashRgb;
    private float flash;
    private int flashTicks;
    private int vignetteRgb;
    private float vignette;
    private int vignetteTicks;
    private float aberration;
    private int aberrationTicks;
    private float zoomBlur;
    private int zoomBlurTicks;
    private float desaturate;
    private int desaturateTicks;

    private ScreenPulse() {
    }

    public static ScreenPulse of() {
        return new ScreenPulse();
    }

    /** Camera shake added at once (0..1; decays on its own like any bhfx shake). */
    public ScreenPulse shake(float amount) {
        this.shake = clamp01(amount);
        return this;
    }

    /** Flash of colour, strongest at once then fading over {@code ticks}. Strength above 1 washes to white. */
    public ScreenPulse flash(int rgb, float strength, int ticks) {
        this.flashRgb = rgb & 0xFFFFFF;
        this.flash = Mth.clamp(strength, 0f, 2f);
        this.flashTicks = ticks(ticks);
        return this;
    }

    /** Tinted screen edges, easing in over 2 ticks then out. */
    public ScreenPulse vignette(int rgb, float strength, int ticks) {
        this.vignetteRgb = rgb & 0xFFFFFF;
        this.vignette = clamp01(strength);
        this.vignetteTicks = ticks(ticks);
        return this;
    }

    /** Colour fringes towards the screen edges. */
    public ScreenPulse aberration(float amount, int ticks) {
        this.aberration = Mth.clamp(amount, 0f, 2f);
        this.aberrationTicks = ticks(ticks);
        return this;
    }

    /** Radial blur towards the screen centre. */
    public ScreenPulse zoomBlur(float amount, int ticks) {
        this.zoomBlur = Mth.clamp(amount, 0f, 2f);
        this.zoomBlurTicks = ticks(ticks);
        return this;
    }

    /** Drain colour from the picture, easing in over 2 ticks then out. */
    public ScreenPulse desaturate(float amount, int ticks) {
        this.desaturate = clamp01(amount);
        this.desaturateTicks = ticks(ticks);
        return this;
    }

    private static float clamp01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }

    private static int ticks(int t) {
        return Mth.clamp(t, 1, 200);
    }

    // ------------------------------------------------------------------ read by bhfx

    public float shake() { return shake; }
    public int flashRgb() { return flashRgb; }
    public float flash() { return flash; }
    public int flashTicks() { return flashTicks; }
    public int vignetteRgb() { return vignetteRgb; }
    public float vignette() { return vignette; }
    public int vignetteTicks() { return vignetteTicks; }
    public float aberration() { return aberration; }
    public int aberrationTicks() { return aberrationTicks; }
    public float zoomBlur() { return zoomBlur; }
    public int zoomBlurTicks() { return zoomBlurTicks; }
    public float desaturate() { return desaturate; }
    public int desaturateTicks() { return desaturateTicks; }

    /** Ticks until every part has faded. */
    public int length() {
        return Math.max(Math.max(Math.max(flashTicks, vignetteTicks), Math.max(aberrationTicks, zoomBlurTicks)), desaturateTicks);
    }

    public boolean isEmpty() {
        return shake <= 0f && flash <= 0f && vignette <= 0f && aberration <= 0f && zoomBlur <= 0f && desaturate <= 0f;
    }

    @ApiStatus.Internal
    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(shake);
        buf.writeInt(flashRgb);
        buf.writeFloat(flash);
        buf.writeVarInt(flashTicks);
        buf.writeInt(vignetteRgb);
        buf.writeFloat(vignette);
        buf.writeVarInt(vignetteTicks);
        buf.writeFloat(aberration);
        buf.writeVarInt(aberrationTicks);
        buf.writeFloat(zoomBlur);
        buf.writeVarInt(zoomBlurTicks);
        buf.writeFloat(desaturate);
        buf.writeVarInt(desaturateTicks);
    }

    /** Values are clamped again, so a modified packet cannot ask for more than the builder allows. */
    @ApiStatus.Internal
    public static ScreenPulse read(FriendlyByteBuf buf) {
        ScreenPulse p = new ScreenPulse();
        p.shake(buf.readFloat());
        int flashRgb = buf.readInt();
        float flash = buf.readFloat();
        p.flash(flashRgb, flash, buf.readVarInt());
        int vignetteRgb = buf.readInt();
        float vignette = buf.readFloat();
        p.vignette(vignetteRgb, vignette, buf.readVarInt());
        float aberration = buf.readFloat();
        p.aberration(aberration, buf.readVarInt());
        float zoomBlur = buf.readFloat();
        p.zoomBlur(zoomBlur, buf.readVarInt());
        float desaturate = buf.readFloat();
        p.desaturate(desaturate, buf.readVarInt());
        return p;
    }
}
