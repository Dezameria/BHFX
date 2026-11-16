package net.deizyn.bhfx.impl.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** A linked fullscreen program with a uniform-location cache. */
final class ShaderProgram {
    private static final int GL_SAMPLER_BUFFER = 0x8DC2;
    private static final int GL_INT_SAMPLER_BUFFER = 0x8DD0;
    private static final int GL_UNSIGNED_INT_SAMPLER_BUFFER = 0x8DD8;

    private final int id;
    private final Map<String, Integer> locations = new HashMap<>();
    private int[] bufferSamplers;

    ShaderProgram(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }

    /** Cached location; -1 when the shader does not use the uniform. */
    int location(String name) {
        Integer cached = locations.get(name);
        if (cached == null) {
            cached = GL20.glGetUniformLocation(id, name);
            locations.put(name, cached);
        }
        return cached;
    }

    /**
     * Locations of every active {@code samplerBuffer} uniform (found once). A sampler uniform starts at unit 0, where
     * a {@code sampler2D} sits, and two sampler types on one unit make the draw an invalid operation, so these must
     * always be pointed somewhere else, even when the effect binds nothing to them.
     */
    int[] bufferSamplers() {
        if (bufferSamplers == null) {
            int count = GL20.glGetProgrami(id, GL20.GL_ACTIVE_UNIFORMS);
            IntBuffer size = BufferUtils.createIntBuffer(1);
            IntBuffer type = BufferUtils.createIntBuffer(1);
            int[] found = new int[count];
            int n = 0;
            for (int i = 0; i < count; i++) {
                size.clear();
                type.clear();
                String name = GL20.glGetActiveUniform(id, i, size, type);
                int t = type.get(0);
                if (t == GL_SAMPLER_BUFFER || t == GL_INT_SAMPLER_BUFFER || t == GL_UNSIGNED_INT_SAMPLER_BUFFER) {
                    int loc = location(name);
                    if (loc >= 0) {
                        found[n++] = loc;
                    }
                }
            }
            bufferSamplers = Arrays.copyOf(found, n);
        }
        return bufferSamplers;
    }

    void delete() {
        GL20.glDeleteProgram(id);
    }
}
