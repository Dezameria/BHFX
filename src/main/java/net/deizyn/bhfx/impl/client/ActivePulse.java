package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.api.ScreenPulse;

/** One screen pulse playing on this client, already weighted by distance. */
final class ActivePulse {
    private static final float EASE_IN = 2f;

    private final ScreenPulse pulse;
    private final float weight;
    private int age;

    ActivePulse(ScreenPulse pulse, float weight) {
        this.pulse = pulse;
        this.weight = weight;
    }

    void tick() {
        age++;
    }

    boolean finished() {
        return age > pulse.length();
    }

    void apply(ScreenParamsImpl screen, float partial) {
        float t = age + partial;
        if (pulse.flash() > 0f) {
            screen.flash(pulse.flashRgb(), pulse.flash() * weight * fade(t, pulse.flashTicks(), 0f));
        }
        if (pulse.vignette() > 0f) {
            screen.vignette(pulse.vignetteRgb(), pulse.vignette() * weight * fade(t, pulse.vignetteTicks(), EASE_IN));
        }
        if (pulse.aberration() > 0f) {
            screen.aberration(pulse.aberration() * weight * fade(t, pulse.aberrationTicks(), 0f));
        }
        if (pulse.zoomBlur() > 0f) {
            screen.zoomBlur(pulse.zoomBlur() * weight * fade(t, pulse.zoomBlurTicks(), 0f));
        }
        if (pulse.desaturate() > 0f) {
            screen.desaturate(pulse.desaturate() * weight * fade(t, pulse.desaturateTicks(), EASE_IN));
        }
    }

    /** Rise over easeIn ticks (instant when 0), then a quadratic fall to zero at length. */
    private static float fade(float t, int length, float easeIn) {
        if (t >= length) {
            return 0f;
        }
        float in = Math.min(easeIn, length * 0.3f);
        if (t < in) {
            return t / in;
        }
        float x = 1f - (t - in) / Math.max(length - in, 1e-3f);
        return x * x;
    }
}
