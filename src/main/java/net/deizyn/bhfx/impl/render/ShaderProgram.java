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

    /** Uniforms set on every draw, by index: no name lookup ({@link #FAST} holds the names). */
    static final String[] FAST = {"InvProj", "InvView", "ViewProj", "OutSize", "ScreenSize", "GameTime", "Eye", "FogColor", "FogRange", "DiffuseSampler", "SceneSampler", "DepthSampler", "POrigin", "PTime", "PMotion", "PShape", "PMode", "POrbit", "POrbitAxis", "PRibbonNormal", "PTurb", "PRing", "PWindow", "PRampRange", "PCurves", "PColorKey0", "PColorKey1", "PColorKey2", "PColorKey3", "PAlphaKeys", "PSizeKeys"};
    static final int U_InvProj = 0;
    static final int U_InvView = 1;
    static final int U_ViewProj = 2;
    static final int U_OutSize = 3;
    static final int U_ScreenSize = 4;
    static final int U_GameTime = 5;
    static final int U_Eye = 6;
    static final int U_FogColor = 7;
    static final int U_FogRange = 8;
    static final int U_DiffuseSampler = 9;
    static final int U_SceneSampler = 10;
    static final int U_DepthSampler = 11;
    static final int U_POrigin = 12;
    static final int U_PTime = 13;
    static final int U_PMotion = 14;
    static final int U_PShape = 15;
    static final int U_PMode = 16;
    static final int U_POrbit = 17;
    static final int U_POrbitAxis = 18;
    static final int U_PRibbonNormal = 19;
    static final int U_PTurb = 20;
    static final int U_PRing = 21;
    static final int U_PWindow = 22;
    static final int U_PRampRange = 23;
    static final int U_PCurves = 24;
    static final int U_PColorKey0 = 25;
    static final int U_PColorKey1 = 26;
    static final int U_PColorKey2 = 27;
    static final int U_PColorKey3 = 28;
    static final int U_PAlphaKeys = 29;
    static final int U_PSizeKeys = 30;
    private final Slot[] fast = new Slot[FAST.length];

    private Slot fast(int index) {
        Slot s = fast[index];
        if (s == null) {
            s = slot(FAST[index]);
            fast[index] = s;
        }
        return s;
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
        set1(slot(name), x);
    }

    void set1(int index, float x) {
        set1(fast(index), x);
    }

    private static void set1(Slot s, float x) {
        if (s.loc >= 0 && !(s.n == 1 && s.x == x)) {
            GL20.glUniform1f(s.loc, x);
            s.n = 1;
            s.x = x;
        }
    }

    void set2(String name, float x, float y) {
        set2(slot(name), x, y);
    }

    void set2(int index, float x, float y) {
        set2(fast(index), x, y);
    }

    private static void set2(Slot s, float x, float y) {
        if (s.loc >= 0 && !(s.n == 2 && s.x == x && s.y == y)) {
            GL20.glUniform2f(s.loc, x, y);
            s.n = 2;
            s.x = x;
            s.y = y;
        }
    }

    void set3(String name, float x, float y, float z) {
        set3(slot(name), x, y, z);
    }

    void set3(int index, float x, float y, float z) {
        set3(fast(index), x, y, z);
    }

    private static void set3(Slot s, float x, float y, float z) {
        if (s.loc >= 0 && !(s.n == 3 && s.x == x && s.y == y && s.z == z)) {
            GL20.glUniform3f(s.loc, x, y, z);
            s.n = 3;
            s.x = x;
            s.y = y;
            s.z = z;
        }
    }

    void set4(String name, float x, float y, float z, float w) {
        set4(slot(name), x, y, z, w);
    }

    void set4(int index, float x, float y, float z, float w) {
        set4(fast(index), x, y, z, w);
    }

    private static void set4(Slot s, float x, float y, float z, float w) {
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
        setInt(slot(name), v);
    }

    void setInt(int index, int v) {
        setInt(fast(index), v);
    }

    private static void setInt(Slot s, int v) {
        if (s.loc >= 0 && !(s.n == 5 && s.i == v)) {
            GL20.glUniform1i(s.loc, v);
            s.n = 5;
            s.i = v;
        }
    }

    /** {@code buffer}: scratch for 16 floats. */
    void setMatrix(int index, Matrix4f matrix, FloatBuffer buffer) {
        Slot s = fast(index);
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
