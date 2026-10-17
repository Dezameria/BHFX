package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;

/**
 * Full-screen effects for this frame. Values from every effect are merged (rule in each method), then scaled by the
 * player's comfort settings, so effects never need to check those themselves.
 */
public interface ScreenParams {
    /** Camera shake for this frame, 0..1 (max). */
    ScreenParams shake(float amount);

    /** Additive flash; strength above 1 washes towards white. Strongest wins (colour and strength). */
    ScreenParams flash(int rgb, float strength);

    /** Tinted edges, 0..1. Strongest wins. */
    ScreenParams vignette(int rgb, float strength);

    /** 0..1 towards grey (max). */
    ScreenParams desaturate(float amount);

    /** Red/blue split growing towards the edges, 0..1 (max). */
    ScreenParams aberration(float amount);

    /** Radial blur towards the screen centre, 0..1 (max). */
    ScreenParams zoomBlur(float amount);

    /** Glow around light the passes added; radius 1 = normal, larger = wider (max of each). */
    ScreenParams bloom(float strength, float radius);

    /** Anime impact frame centred on a world point, 0..1. Strongest wins. Ignored when the player turned impact frames off. */
    ScreenParams impact(ImpactStyle style, float strength, Vec3 focus);

    /** The image ripples in a ring round a world point: radius and width in blocks, strength 0..1. Up to 8 per frame. */
    ScreenParams ripple(Vec3 center, float radius, float width, float strength);
}
