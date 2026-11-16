package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * A table of {@code vec4} values that an effect fills in Java and its shaders read, made with {@link Fx#createData}.
 * For data too big for uniforms: per-piece tables (where each shard flies, how it spins), bone frames for an SDF body,
 * ground heights looked up per point, lists of objects a pass loops over.
 * <ul>
 *   <li><b>Writing</b>: from {@code onStart}, {@code tick} or {@code passes}. Only the changed range is sent to the GPU,
 *       the next time a pass binds the table, so writing a few entries every tick costs only those entries.</li>
 *   <li><b>Reading</b>: bind it with {@link Uniforms#data} to {@code uniform samplerBuffer Name;} and read entry
 *       {@code i} with {@code fxData(Name, i)} from {@code #include "bhfx:data.glsl"} (zero outside the table). Pass and
 *       particle-material setups can both bind it.</li>
 *   <li><b>Same on every client</b>: fill it from {@link Fx#random()}, the params or world reads. Nothing is sent over
 *       the network.</li>
 *   <li>Floats only ({@code RGBA32F}). Integers are exact up to 2^24.</li>
 *   <li>At most {@link #MAX_SIZE} entries (1 MB). Each bind uses one texture unit from the same pool as
 *       {@link Uniforms#texture} (10 per pass).</li>
 *   <li>Freed with the effect. When post is off it only stores values in Java.</li>
 * </ul>
 */
@ApiStatus.Experimental
public interface FxData {
    /** The most entries a table can hold: GL 3.1's smallest allowed {@code GL_MAX_TEXTURE_BUFFER_SIZE}. */
    int MAX_SIZE = 65536;

    /** Number of {@code vec4} entries. */
    int size();

    /** Set entry {@code index}. */
    FxData set(int index, float x, float y, float z, float w);

    /** Set entry {@code index} to {@code (v, w)}. */
    default FxData set(int index, Vec3 v, float w) {
        return set(index, (float) v.x, (float) v.y, (float) v.z, w);
    }

    /** Write {@code values} (4 floats per entry, so its length must be a multiple of 4) from entry {@code index} on. */
    FxData put(int index, float[] values);

    /** Set entries {@code from} (inclusive) to {@code to} (exclusive) to the same value. */
    FxData fill(int from, int to, float x, float y, float z, float w);

    /** Read back component {@code component} (0..3 = x..w) of entry {@code index}. */
    float get(int index, int component);

    /**
     * Write a frame into entries {@code first} .. {@code first + 2}: (x axis, origin.x), (y axis, origin.y),
     * (z axis, origin.z). The shader reads it with {@code fxFrame(table, first)} ({@code bhfx:data.glsl}). Give a
     * camera-relative frame ({@link FxFrame#rel}).
     */
    default FxData setFrame(int first, FxFrame frame) {
        Vec3 o = frame.origin();
        set(first, frame.x(), (float) o.x);
        set(first + 1, frame.y(), (float) o.y);
        return set(first + 2, frame.z(), (float) o.z);
    }

    /**
     * Write model instance {@code index} for {@link PassList#models}: entries {@code 4 * index} .. {@code + 3}, the
     * frame (camera-relative, {@link FxFrame#rel}) with its axes times {@code scale}, then four free values the
     * material reads as {@code s.instance} (e.g. how far it has dissolved, how hot it glows).
     */
    default FxData setInstance(int index, FxFrame frame, float scale, float a, float b, float c, float d) {
        int first = index * 4;
        Vec3 o = frame.origin();
        set(first, frame.x().scale(scale), (float) o.x);
        set(first + 1, frame.y().scale(scale), (float) o.y);
        set(first + 2, frame.z().scale(scale), (float) o.z);
        return set(first + 3, a, b, c, d);
    }
}
