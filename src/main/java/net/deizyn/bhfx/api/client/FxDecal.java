package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * One textured mark laid onto the world for this frame, from {@link PassList#decal}: a magic circle on the ground, a
 * scorch, a crack, a sigil on a wall. Set what differs from the defaults right after the call; the object is reused
 * by the next {@code decal} call, so do not keep it.
 * <p>
 * Defaults: flat on the ground (facing up), not turned, white, fully opaque where the texture is, colour and coverage
 * both from the texture (rgb, alpha), the whole texture, reaching 1 block above and below its plane.
 */
@ApiStatus.Experimental
public interface FxDecal {
    /** Turn round the facing direction, in degrees. On the ground: clockwise seen from above. */
    FxDecal rotation(float degrees);

    /** Tint (0xRRGGBB, multiplied with the texture's colour) and opacity 0..1. */
    FxDecal color(int rgb, float alpha);

    /**
     * Add the mark's light to the surface instead of covering it (glowing sigils, hot cracks); values above 1 in
     * {@code brightness} make it bloom. Without this the mark covers the surface like paint (scorch, blood, shadow).
     */
    FxDecal glow(float brightness);

    /**
     * Take the coverage from the texture's red channel and the colour from {@link #color} alone: for grayscale
     * textures, which read as {@code (v, v, v, 1)} and would otherwise be an opaque square.
     */
    FxDecal mask();

    /**
     * The direction the mark faces (its plane's normal): up for the ground (default), a horizontal direction for a
     * wall, down for a ceiling. Surfaces turned away from it by more than about 60 degrees are left alone, so a
     * ground mark does not smear down a cliff.
     */
    FxDecal facing(Vec3 normal);

    /** How far in front of and behind its plane the mark still lands on surfaces, in blocks (default 1). */
    FxDecal depth(float blocks);

    /** Show one cell of a grid of pictures (a flipbook): cells count from the top-left, row by row. */
    FxDecal frame(int columns, int rows, int index);
}
