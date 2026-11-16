package net.deizyn.bhfx.impl.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import org.joml.Matrix4f;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A linked program with a uniform cache: locations, and the last value sent through {@link #set1} .. {@link #setMatrix},
 * so a value that did not change is not sent again (uniforms stay in the program between draws and frames). A uniform
 * must always go through these or always be set directly by location, never both.
 */
final class ShaderProgram {
    private static final int GL_SAMPLER_BUFFER = 0x8DC2;
    private static final int GL_INT_SAMPLER_BUFFER = 0x8DD0;
    private static final int GL_UNSIGNED_INT_SAMPLER_BUFFER = 0x8DD8;

    private final int id;
    /** Location and last value of one uniform. {@code n}: 0 nothing sent yet, 1..4 floats, 5 int, 16 matrix. */
    private static final class Slot {
        final int loc;
        int n;
        float x, y, z, w;
        int i;
        float[] m;

        Slot(int loc) {
            this.loc = loc;
        }
    }

    private final Map<String, Slot> slots = new HashMap<>();
    private final float[] matrixScratch = new float[16];
    private int[] bufferSamplers;

    ShaderProgram(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }

    /** Cached location; -1 when the shader does not use the uniform. */
    int location(String name) {
        return slot(name).loc;
    }

    private Slot slot(String name) {
        Slot s = slots.get(name);
        if (s == null) {
            s = new Slot(GL20.glGetUniformLocation(id, name));
            slots.put(name, s);
        }
        return s;
    }

    // The setters below need this program in use. NaN never equals itself, so it is always sent.

    void set1(String name, float x) {
        Slot s = slot(name);
        if (s.loc >= 0 && !(s.n == 1 && s.x == x)) {
            GL20.glUniform1f(s.loc, x);
            s.n = 1;
            s.x = x;
        }
    }

    void set2(String name, float x, float y) {
        Slot s = slot(name);
        if (s.loc >= 0 && !(s.n == 2 && s.x == x && s.y == y)) {
            GL20.glUniform2f(s.loc, x, y);
            s.n = 2;
            s.x = x;
            s.y = y;
        }
    }

    void set3(String name, float x, float y, float z) {
        Slot s = slot(name);
        if (s.loc >= 0 && !(s.n == 3 && s.x == x && s.y == y && s.z == z)) {
            GL20.glUniform3f(s.loc, x, y, z);
            s.n = 3;
            s.x = x;
            s.y = y;
            s.z = z;
        }
    }

    void set4(String name, float x, float y, float z, float w) {
        Slot s = slot(name);
        if (s.loc >= 0 && !(s.n == 4 && s.x == x && s.y == y && s.z == z && s.w == w)) {
            GL20.glUniform4f(s.loc, x, y, z, w);
            s.n = 4;
            s.x = x;
            s.y = y;
            s.z = z;
            s.w = w;
        }
    }

    void setInt(String name, int v) {
        Slot s = slot(name);
        if (s.loc >= 0 && !(s.n == 5 && s.i == v)) {
            GL20.glUniform1i(s.loc, v);
            s.n = 5;
            s.i = v;
        }
    }

    /** {@code buffer}: scratch for 16 floats. */
    void setMatrix(String name, Matrix4f matrix, FloatBuffer buffer) {
        Slot s = slot(name);
        if (s.loc < 0) {
            return;
        }
        matrix.get(matrixScratch);
        if (s.n == 16 && Arrays.equals(s.m, matrixScratch)) {
            return;
        }
        if (s.m == null) {
            s.m = new float[16];
        }
        System.arraycopy(matrixScratch, 0, s.m, 0, 16);
        s.n = 16;
        buffer.clear();
        buffer.put(matrixScratch).flip();
        GL20.glUniformMatrix4fv(s.loc, false, buffer);
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
