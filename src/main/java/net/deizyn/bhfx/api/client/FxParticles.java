package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * A batch of GPU particles that share one material shader, made with {@link Fx#createParticles}. Spawn them from
 * {@code tick} or {@code passes}, and draw the batch each frame with {@link PassList#particles}.
 * <ul>
 *   <li><b>Material</b>: {@code "ns:name"} loads {@code assets/ns/shaders/bhfx/particle/name.fsh}, which defines
 *       {@code vec4 material(Particle p)} (see {@code bhfx:particle.glsl}). It may read the scene, depth and textures
 *       like a pass.</li>
 *   <li><b>Motion</b> is computed on the GPU from the spawn values (position, velocity, gravity, drag, and the
 *       {@linkplain ParticleSpawn#orbit orbit} / {@linkplain ParticleSpawn#pull pull} round the batch's
 *       {@link #orbit} axis), so a particle costs no CPU after it is spawned and no data is sent while nothing new
 *       spawns.</li>
 *   <li><b>Spawning</b>: one at a time with {@link #spawn}, many from a shape with {@link #emit}
 *       ({@link FxEmitter}), or at a steady rate with a {@link #stream}.</li>
 *   <li>Drawn into bhfx's HDR chain at the batch's pass order: hidden by terrain and entities in front (from the
 *       depth buffer), softly faded where they meet geometry, and bright colours (above 1) bloom. Particles draw
 *       over water and glass, like passes.</li>
 *   <li>The batch is a ring of {@code capacity} particles: when full, a new spawn replaces the oldest.</li>
 *   <li>Freed with the effect.</li>
 * </ul>
 * Times are seconds, distances blocks, velocities blocks per second. Use {@link Fx#random()} for spawn randomness so
 * every client shows the same thing.
 */
@ApiStatus.Experimental
public interface FxParticles {
    /**
     * Built-in material: a soft round dot, coloured by {@code p.color}. With {@link Blend#ADD} its core glows; with
     * {@link Blend#ALPHA} it is a soft puff (smoke, dust). Fades in and out briefly. Needs no uniforms: shape it with
     * {@link #colorOverLife}, {@link #alphaOverLife} and {@link #sizeOverLife}.
     */
    ResourceLocation DOT = new ResourceLocation("bhfx", "dot");
    /** Built-in material: a soft streak along the quad's x, brighter at the front. For {@code VELOCITY} / {@code AXIS}. */
    ResourceLocation STREAK = new ResourceLocation("bhfx", "streak");
    /**
     * Built-in material: a texture. Bind it with {@code u.texture("Sprite", tex)}; optional
     * {@code u.f4("SpriteGrid", cols, rows, frames, mask)} plays a flipbook over the life ({@code frames} &gt; 1) and,
     * with {@code mask} = 1, uses the red channel as coverage (grayscale images) instead of the alpha.
     */
    ResourceLocation SPRITE = new ResourceLocation("bhfx", "sprite");
    /** Built-in material: a soft band for {@link Facing#RIBBON}, brighter in the middle. */
    ResourceLocation RIBBON = new ResourceLocation("bhfx", "ribbon");

    /** How a particle's quad is turned. */
    enum Facing {
        /** Faces the camera (the usual sprite). */
        CAMERA,
        /** Long side along the velocity, turned to the camera around it: sparks, streaks. See {@link #stretch}. */
        VELOCITY,
        /** Lies flat in the world's x/z plane: ground rings, decals. */
        FLAT,
        /**
         * Long side along each particle's own {@link ParticleSpawn#axis axis}, for its own length: bolts, beams,
         * slashes radiating in every direction. {@link ParticleSpawn#rotation} tilts the quad round the axis away
         * from facing the camera (0 faces it, π/2 is edge-on), which shows the angle in depth. {@code p.uv.x} runs
         * along the axis, 0 at the base. Use with {@link #pivot}(1) to grow out from the spawn point.
         */
        AXIS,
        /**
         * A connected strip through the points of each {@link Ribbon} (and each {@link Stream}), oldest to newest,
         * {@link ParticleSpawn#size size} wide, turned to the camera: trails, sword arcs, spiralling bands. Each point
         * moves like any particle (velocity, gravity, orbit, turbulence) and dies with its life, so the tail shortens
         * as points expire; fade it with {@link #alphaOverLife}. {@code p.uv.y} runs across, {@code p.age} along
         * (0 at the newest point); {@code p.uv.x} only within one segment. Don't set {@link ParticleSpawn#axis} on
         * ribbon points (it holds the link).
         */
        RIBBON
    }

    /** How a particle mixes into the image. */
    enum Blend {
        /** Adds light; order-independent. For glow, fire, magic. */
        ADD,
        /** Covers what is behind it (premultiplied alpha). For smoke, dust. Not sorted. */
        ALPHA,
        /**
         * Bends the image behind it instead of drawing: the material returns an offset in {@code xy} (-1..1, along the
         * quad's own axes) and its strength in {@code a}. Heat haze, shock bubbles. See {@link #distortion}.
         */
        DISTORT
    }

    ResourceLocation material();

    /** Default {@link Blend#ADD}. */
    FxParticles blend(Blend blend);

    /** Default {@link Facing#CAMERA}. */
    FxParticles facing(Facing facing);

    /** {@link Facing#VELOCITY}: the quad's length grows by {@code secondsOfTravel} × speed. Default 0.05. */
    FxParticles stretch(float secondsOfTravel);

    /**
     * Where the spawn point sits along the quad's x: 0 = centre (default), 1 = at its base (uv.x = 0), so the quad
     * reaches out from the spawn point along its axis / motion / right side.
     */
    FxParticles pivot(float pivot);

    /** Constant acceleration in blocks/s² for every particle of the batch. Default none. */
    FxParticles gravity(double x, double y, double z);

    /** Velocity lost per second (exponential, 0 = none, 2 = about 86% gone after a second). Default 0. */
    FxParticles drag(float perSecond);

    /** Fade-out distance, in blocks, where a particle meets terrain; 0 = hard edge. Default 0.4. */
    FxParticles softness(float blocks);

    /** {@link Blend#DISTORT}: offset scale as a share of the particle's size on screen. Default 0.08. */
    FxParticles distortion(float strength);

    /**
     * Wobble every particle smoothly by up to about {@code blocks}, changing {@code frequency} times a second.
     * Nearby particles move alike ({@link #turbulence(float, float, float) scale}), so smoke and embers drift as if in
     * moving air. Starts at no offset at birth. Computed on the GPU; 0 = off (default).
     */
    FxParticles turbulence(float blocks, float frequency);

    /** As {@link #turbulence(float, float)}; {@code scale} = waves per block of the pattern (default 0.35; 0 = every particle alike). */
    FxParticles turbulence(float blocks, float frequency, float scale);

    /**
     * Multiply each particle's colour by these display-encoded 0xRRGGBB colours over its life, evenly spaced from birth
     * to death (1..4 keys; none = off). Every material that uses {@code p.color} follows it.
     */
    FxParticles colorOverLife(int... colours);

    /**
     * Multiply each particle's colour by a horizontal ramp texture over its life, read from {@code from} at birth to
     * {@code to} at death (0..1 across the image; e.g. 1, 0 to run a cold-to-hot ramp backwards). Until the texture
     * has loaded the colours are left as they are. {@code null} = off.
     */
    FxParticles colorOverLife(FxTexture ramp, float from, float to);

    /** Multiply each particle's alpha by these values over its life (1..4 keys, evenly spaced; none = off). */
    FxParticles alphaOverLife(float... keys);

    /** Multiply each particle's size by these values over its life (1..4 keys, evenly spaced; none = off). */
    FxParticles sizeOverLife(float... keys);

    /**
     * The axis particles {@linkplain ParticleSpawn#orbit orbit} round and are {@linkplain ParticleSpawn#pull pulled}
     * towards: through {@code centre}, along {@code axis} (any length; a positive orbit turns counter-clockwise seen
     * from the axis tip, so anticlockwise from above for the default up axis). Default: through the batch's first spawn, straight up. Applies to every particle of the batch at once,
     * also those already flying: moving it each frame carries a whirlwind along with its caster. An orbiting (or
     * pulled) particle keeps its place relative to the centre as it was when the particle was spawned, so set the
     * centre before spawning, from the same position (a frame position for particles spawned in {@code passes}; a
     * tick position runs ahead of the frame's, which kinks ribbons on a moving caster). Particles that don't orbit stay
     * where they are in the world.
     * <p>
     * An orbiting particle's own velocity and the batch gravity turn with it (they are in the turning frame), so a
     * velocity along the axis rises straight, and one outwards spirals out.
     */
    FxParticles orbit(Vec3 centre, Vec3 axis);

    FxParticles orbit(double x, double y, double z, double axisX, double axisY, double axisZ);

    /**
     * Start one particle at a world position. Set its other values on the returned object right away (it is reused
     * by the next spawn). Spawning is cheap even while post-processing is off; nothing is drawn then.
     */
    ParticleSpawn spawn(Vec3 world);

    ParticleSpawn spawn(double x, double y, double z);

    /**
     * Spawn {@code count} particles at {@code centre} spread over {@code shape}, each moving along the shape's
     * direction at {@code speed}, and call {@code each} (may be null) to set the rest. Uses the effect's
     * {@link Fx#random()}, so every client gets the same spread when emitting from {@code tick} (emitting from
     * {@code passes} draws on it once per frame, and frame rates differ, so later spawns differ between clients).
     * Returns {@code count}.
     */
    int emit(FxEmitter.Shape shape, Vec3 centre, int count, double speed, FxEmitter.EmitAction each);

    int emit(FxEmitter.Shape shape, double x, double y, double z, int count, double speed, FxEmitter.EmitAction each);

    /**
     * A steady flow of particles from this batch, {@code perSecond} of them. Make it once and keep it; call
     * {@link Stream#emit} every tick or frame while it should flow.
     */
    Stream stream(float perSecond);

    /**
     * A new chain of points for {@link Facing#RIBBON}: each {@link Ribbon#spawn} joins the one before. Several ribbons
     * can share a batch. (A {@link Stream} keeps its own.)
     */
    Ribbon ribbon();

    /** Particles spawned and not yet expired. */
    int alive();

    int capacity();

    /** Remove every particle at once. */
    void clear();

    /** Values of the particle last spawned. Defaults: still, 1 s life, size 0.25, no spin, white, data 0, no delay. */
    @ApiStatus.Experimental
    interface ParticleSpawn {
        ParticleSpawn velocity(double x, double y, double z);

        ParticleSpawn velocity(Vec3 velocity);

        ParticleSpawn life(float seconds);

        /** Half-size in blocks (the quad spans ±size). */
        ParticleSpawn size(float size);

        /** Half-size at birth and at death, linear in between. */
        ParticleSpawn size(float start, float end);

        /** Quad rotation in radians and its spin per second (camera and flat facing). */
        ParticleSpawn rotation(float radians, float spinPerSecond);

        /**
         * 0xRRGGBB as it looks on screen, with alpha, as {@code p.color}. Not converted to linear (unlike
         * {@link Uniforms#color}): the image particles draw onto is display-encoded, so the colour shows as given.
         */
        ParticleSpawn color(int rgb, float alpha);

        /** Colour in the same display-encoded space, 0..1 per channel; values above 1 glow. */
        ParticleSpawn color(float r, float g, float b, float a);

        /** Four free values for the material, as {@code p.data}. */
        ParticleSpawn data(float x, float y, float z, float w);

        /** Appear this many seconds later (bursts that ripple outwards without spawning across ticks). */
        ParticleSpawn delay(float seconds);

        /**
         * {@link Facing#AXIS}: the direction of the long side (any length; normalised) and the quad's full length in
         * blocks. {@link #size} stays the half-width across it. Without this, the axis is up and the length 2 × size.
         */
        ParticleSpawn axis(double x, double y, double z, float length);

        ParticleSpawn axis(Vec3 direction, float length);

        /**
         * Turn round the batch's {@link FxParticles#orbit} axis at {@code radiansPerSecond} (negative = the other
         * way), speeding up by {@code spinUp} rad/s². The distance from the axis stays as it is unless the particle's
         * velocity or {@link #pull} changes it.
         */
        ParticleSpawn orbit(float radiansPerSecond, float spinUp);

        /**
         * Move towards the batch's orbit axis (the line, not its centre point) at this speed in blocks/s (negative =
         * away), slowed by the batch drag like the velocity. Stops on the axis instead of passing through it. With {@link #orbit}: spirals into a
         * black hole, or out of a vortex.
         */
        ParticleSpawn pull(float blocksPerSecond);
    }

    /**
     * Spawns particles at a steady rate, spread evenly in time between calls and along the way the centre moved since
     * the last call, so trails behind fast things stay even. Made with {@link FxParticles#stream}.
     */
    @ApiStatus.Experimental
    interface Stream {
        Stream rate(float perSecond);

        float rate();

        /**
         * Spawn what is due since the last call (none on the first call, which only starts the clock), each from
         * {@code shape} round the centre's position at its birth time, moving along the shape's direction at
         * {@code speed}; {@code each} (may be null) sets the rest. Gaps over 0.25 s are not caught up. Returns how
         * many spawned.
         */
        int emit(FxEmitter.Shape shape, Vec3 centre, double speed, FxEmitter.EmitAction each);

        int emit(FxEmitter.Shape shape, double x, double y, double z, double speed, FxEmitter.EmitAction each);

        /**
         * Forget the last call: the next one starts the clock again and nothing is drawn along a jump. In a
         * {@link Facing#RIBBON} batch it also starts a new ribbon.
         */
        void reset();
    }

    /** One chain of ribbon points, made with {@link FxParticles#ribbon}. */
    @ApiStatus.Experimental
    interface Ribbon {
        /** Spawn the next point, joined to the previous one (as {@link FxParticles#spawn}). */
        ParticleSpawn spawn(double x, double y, double z);

        ParticleSpawn spawn(Vec3 world);

        /** End this chain: the next point starts a new one. */
        void cut();
    }
}
