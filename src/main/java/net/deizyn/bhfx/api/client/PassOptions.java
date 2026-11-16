package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @param order  lower draws first, sorted across all effects. Suggested: world-space 0, particles 50, full-screen
 *               overlays 100.
 * @param bounds world-space sphere the pass stays inside, or null for the whole screen (see {@link #within})
 */
public record PassOptions(int order, @Nullable Bounds bounds, float layer) {
    public PassOptions(int order, @Nullable Bounds bounds) {
        this(order, bounds, 0f);
    }

    public static final PassOptions DEFAULT = new PassOptions(0);
    public static final PassOptions OVERLAY = new PassOptions(100);
    /** Default order of particle batches: over world-space passes, under overlays. */
    @ApiStatus.Experimental
    public static final PassOptions PARTICLES = new PassOptions(50);

    public PassOptions(int order) {
        this(order, null);
    }

    /**
     * Promise that this pass changes no pixel outside the screen area of a sphere around {@code center} (world
     * coordinates). bhfx then shades only that area (scissor) and skips the pass while the sphere is off screen, which
     * saves most of the cost of a small or distant effect. The radius must cover everything the pass draws, including
     * glow tails and light it throws on the ground; anything outside is cut off. The pass may still read the input
     * anywhere (for example to bend the scene behind it).
     */
    @ApiStatus.Experimental
    public PassOptions within(Vec3 center, double radius) {
        return new PassOptions(order, new Bounds(center, radius), layer);
    }

    /**
     * Draw this pass as a layer at half resolution (a quarter of the pixels) and lay it over the image with a
     * depth-aware upsample: for soft, costly things (mist, light shafts, glows marched through a volume), not for
     * anything with a sharp outline of its own. The shader must then output the layer, not the finished image:
     * {@code fragColor = vec4(light, coverage)} with premultiplied colour, so the result is
     * {@code image * (1 - coverage) + light}; (0, 0, 0, 0) leaves the image alone. {@code OutSize} is the layer's
     * size. Only for passes added with {@link PassList#add}; {@link #layer} is the resolution share (0 = not a layer).
     */
    @ApiStatus.Experimental
    public PassOptions halfRes() {
        return new PassOptions(order, bounds, 0.5f);
    }

    @ApiStatus.Experimental
    public record Bounds(Vec3 center, double radius) {
    }
}
