package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * Shafts of light through mist, from a point in the world: light pouring down from a rift, a lamp's beam, rays
 * bursting out of an orb. The air inside a cone (or a whole ball) round the source glows, in separate rays that
 * drift slowly.
 * <pre>{@code
 * private final FxShafts shafts = FxShafts.cone(Vec3.ZERO, new Vec3(0, -1, 0), 12f, 22f).color(0xFFD890, 1.2f);
 * // passes(), every frame:
 * shafts.move(rift, down);
 * shafts.draw(frame, out, seconds(frame), fade);
 * }</pre>
 * One pass at half resolution ({@link PassOptions#halfRes()}), 32 steps a pixel, only over the screen area of the
 * shafts. Terrain in front hides them, but nothing casts a shadow into them; they add light to the air only, so put
 * a {@link PassList#light} where they land.
 */
@ApiStatus.Experimental
public final class FxShafts {
    public static final ResourceLocation SHADER = new ResourceLocation("bhfx", "shafts");

    private Vec3 source;
    private Vec3 axis;
    private final float length;
    private final float cos;
    private int rgb = 0xFFFFFF;
    private float strength = 1f;
    private float count = 10f;
    private float sharpness = 0.5f;
    private float mist = 0.15f;
    private float drift = 1f;
    private int order = PassOptions.PARTICLES.order();
    private boolean half = true;

    private FxShafts(Vec3 source, Vec3 axis, float length, float cos) {
        this.source = source;
        this.axis = axis;
        this.length = Math.max(0.1f, length);
        this.cos = cos;
    }

    /**
     * Shafts inside a cone: from {@code source} along {@code direction}, {@code length} blocks long, opening
     * {@code angleDegrees} to each side (1..85).
     */
    public static FxShafts cone(Vec3 source, Vec3 direction, float length, float angleDegrees) {
        float a = Math.max(1f, Math.min(85f, angleDegrees));
        return new FxShafts(source, unit(direction), length, (float) Math.cos(Math.toRadians(a)));
    }

    /** Rays in every direction from {@code source}, {@code radius} blocks long. */
    public static FxShafts around(Vec3 source, float radius) {
        return new FxShafts(source, new Vec3(0, 1, 0), radius, -2f);
    }

    private static Vec3 unit(Vec3 v) {
        return v == null || v.lengthSqr() < 1e-12 ? new Vec3(0, -1, 0) : v.normalize();
    }

    /** Move the source (and turn the cone; {@code direction} may be null to keep it). */
    public FxShafts move(Vec3 source, Vec3 direction) {
        this.source = source;
        if (direction != null) {
            this.axis = unit(direction);
        }
        return this;
    }

    /** Colour (0xRRGGBB) and brightness; 1 is a clear glow at dusk, by day use 2 or more. Default white, 1. */
    public FxShafts color(int rgb, float strength) {
        this.rgb = rgb;
        this.strength = strength;
        return this;
    }

    /**
     * {@code count}: rays per radian across the light (10 = about seven across a 20 degree cone). {@code sharpness}:
     * 0 = wide soft bands, 1 = thin separate rays. Default 10, 0.5.
     */
    public FxShafts rays(float count, float sharpness) {
        this.count = Math.max(0.5f, count);
        this.sharpness = Math.max(0f, Math.min(1f, sharpness));
        return this;
    }

    /** Even glow between the rays, 0 (only rays) .. 1 (no rays, a plain cone of lit mist). Default 0.15. */
    public FxShafts mist(float share) {
        this.mist = Math.max(0f, Math.min(1f, share));
        return this;
    }

    /** How fast the rays shift, 0 = still. Default 1. */
    public FxShafts drift(float speed) {
        this.drift = speed;
        return this;
    }

    /** Pass order. Default: with particles ({@link PassOptions#PARTICLES}). */
    public FxShafts order(int order) {
        this.order = order;
        return this;
    }

    /** Draw at full resolution instead of half (four times the cost; rarely worth it for soft light). */
    public FxShafts fullRes() {
        this.half = false;
        return this;
    }

    /** The sphere that holds the shafts: the pass shades only its screen area. */
    public PassOptions.Bounds bounds() {
        // a cone narrower than about 75 degrees fits a smaller ball round the middle of its axis
        double reach = cos > -1.5f ? Math.sqrt(1.25 - cos) : 2.0;
        if (reach < 1.0) {
            return new PassOptions.Bounds(source.add(axis.scale(length * 0.5)), length * reach + 0.25);
        }
        return new PassOptions.Bounds(source, length + 0.25);
    }

    /** Add the shafts to this frame. {@code seconds}: the effect's clock; {@code fade}: 0..1. */
    public void draw(FrameContext frame, PassList out, float seconds, float fade) {
        if (!(fade * strength > 0.001f) || source == null) {
            return;
        }
        PassOptions.Bounds b = bounds();
        PassOptions options = new PassOptions(order).within(b.center(), b.radius());
        if (half) {
            options = options.halfRes();
        }
        Vec3 rel = frame.rel(source);
        float layer = half ? 1f : 0f;
        out.add(SHADER, options, u -> u
                .f4("Source", (float) rel.x, (float) rel.y, (float) rel.z, length)
                .f4("Axis", (float) axis.x, (float) axis.y, (float) axis.z, cos)
                .f4("Tint", linear(rgb >> 16) * strength, linear(rgb >> 8) * strength, linear(rgb) * strength, mist)
                .f4("P0", seconds, count, sharpness, drift)
                .f4("Mode", layer, fade, 0f, 0f));
    }

    private static float linear(int channel) {
        return (float) Math.pow((channel & 0xFF) / 255.0, 2.2);
    }
}
