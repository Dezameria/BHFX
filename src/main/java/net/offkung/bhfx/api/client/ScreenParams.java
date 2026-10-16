package net.offkung.bhfx.api.client;

/**
 * Full-screen effects for this frame. Values from every effect are merged (strongest wins), then scaled by the
 * player's comfort settings, so effects never need to check those themselves.
 * <p>0.1 subset; bloom, impact frames, zoom blur, freeze and stepped fps arrive in 0.2.
 */
public interface ScreenParams {
    /** Camera shake for this frame, 0..1 (max-merged). */
    ScreenParams shake(float amount);

    /** Additive flash; values above 1 wash towards white. Strongest wins (colour and strength). */
    ScreenParams flash(int rgb, float strength);

    /** Tinted edges, 0..1. Strongest wins. */
    ScreenParams vignette(int rgb, float strength);

    /** 0..1 towards grey (max-merged). */
    ScreenParams desaturate(float amount);

    /** Red/blue split growing towards the edges, 0..1 (max-merged). */
    ScreenParams aberration(float amount);
}
