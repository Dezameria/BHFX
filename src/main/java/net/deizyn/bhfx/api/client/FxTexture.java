package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A texture a pass or a particle material samples, bound with {@link Uniforms#texture}. Full resource paths, e.g.
 * {@code mymod:textures/fx/noise.png}.
 * <ul>
 *   <li>Loading never blocks the frame: the file is decoded on a background thread and uploaded on a later frame.
 *       Until then the sampler reads zero (transparent black). {@link BHFxClient#preload} starts it early.</li>
 *   <li>A grayscale image is stored with one byte per pixel (a quarter of RGBA) and still reads as {@code (v, v, v, 1)}.</li>
 *   <li>{@link #packed} puts up to four grayscale masks into one texture: one fetch reads all four.</li>
 *   <li>Mipmapped and repeating by default (tiling noise); {@link #clamp()} for ramps and sprites.</li>
 *   <li>Read as stored by default: right for masks, noise and offsets, and for colours drawn straight onto bhfx's
 *       image, which is display-encoded like the world. {@link #srgb()} converts colours to linear on sampling (the
 *       space {@link Uniforms#color} gives), only for shaders that light or mix in linear space and convert back.</li>
 *   <li>Freed after 60 s unused, and on resource reload (F3+T reloads edited files).</li>
 * </ul>
 * Equal descriptions share one GPU texture.
 */
@ApiStatus.Experimental
public final class FxTexture {
    private final List<ResourceLocation> sources;
    private final boolean packed;
    private final boolean srgb;
    private final boolean clamp;
    private final boolean nearest;

    private FxTexture(List<ResourceLocation> sources, boolean packed, boolean srgb, boolean clamp, boolean nearest) {
        this.sources = sources;
        this.packed = packed;
        this.srgb = srgb;
        this.clamp = clamp;
        this.nearest = nearest;
    }

    /** One image file. */
    public static FxTexture of(ResourceLocation file) {
        return new FxTexture(List.of(Objects.requireNonNull(file, "file")), false, false, false, false);
    }

    /**
     * Up to four grayscale images in the r, g, b and a channels of one texture (each image's red channel is used).
     * Null leaves a channel at 0; all images must have the same size.
     */
    public static FxTexture packed(ResourceLocation r, @Nullable ResourceLocation g, @Nullable ResourceLocation b,
                                   @Nullable ResourceLocation a) {
        Objects.requireNonNull(r, "r");
        return new FxTexture(Collections.unmodifiableList(Arrays.asList(r, g, b, a)), true, false, false, false);
    }

    /**
     * The image holds sRGB colours and the shader reads them converted to linear. Not for packed textures. Colours
     * written onto the image as they are should not use this (they would come out darker and more saturated).
     */
    public FxTexture srgb() {
        return new FxTexture(sources, packed, !packed, clamp, nearest);
    }

    /** Clamp to the edge instead of repeating. */
    public FxTexture clamp() {
        return new FxTexture(sources, packed, srgb, true, nearest);
    }

    /** Nearest filtering and no mipmaps (pixel art, lookup tables). */
    public FxTexture nearest() {
        return new FxTexture(sources, packed, srgb, clamp, true);
    }

    /** One entry for {@link #of}, four (some null) for {@link #packed}. */
    public List<ResourceLocation> sources() {
        return sources;
    }

    public boolean isPacked() {
        return packed;
    }

    public boolean isSrgb() {
        return srgb;
    }

    public boolean isClamped() {
        return clamp;
    }

    public boolean isNearest() {
        return nearest;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FxTexture t && packed == t.packed && srgb == t.srgb && clamp == t.clamp
                && nearest == t.nearest && sources.equals(t.sources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sources, packed, srgb, clamp, nearest);
    }

    @Override
    public String toString() {
        return (packed ? "packed" + sources : sources.get(0).toString())
                + (srgb ? " srgb" : "") + (clamp ? " clamp" : "") + (nearest ? " nearest" : "");
    }
}
