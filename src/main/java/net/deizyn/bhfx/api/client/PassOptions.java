package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @param order  lower draws first, sorted across all effects. Suggested: world-space 0, full-screen overlays 100.
 * @param bounds world-space sphere the pass stays inside, or null for the whole screen (see {@link #within})
 */
public record PassOptions(int order, @Nullable Bounds bounds) {
    public static final PassOptions DEFAULT = new PassOptions(0);
    public static final PassOptions OVERLAY = new PassOptions(100);

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
        return new PassOptions(order, new Bounds(center, radius));
    }

    @ApiStatus.Experimental
    public record Bounds(Vec3 center, double radius) {
    }
}
