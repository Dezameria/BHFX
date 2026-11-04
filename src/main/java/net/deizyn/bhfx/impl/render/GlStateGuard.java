package net.deizyn.bhfx.impl.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Saves the GL state bhfx touches and puts it back exactly, so Minecraft's own state cache stays correct.
 * Texture units are saved lazily — only the units bhfx actually binds are queried and restored.
 */
final class GlStateGuard {
    private static final int UNITS = 16;

    private final IntBuffer viewportBuf = BufferUtils.createIntBuffer(4);
    private final ByteBuffer maskBuf = BufferUtils.createByteBuffer(16);
    private final int[] viewport = new int[4];
    private final boolean[] colorMask = new boolean[4];
    private final int[] savedTexture = new int[UNITS];
    private final int[] savedSampler = new int[UNITS];

    private int drawFbo;
    private int readFbo;
    private int program;
    private int vao;
    private int activeTexture;
    private boolean depthTest;
    private boolean blend;
    private boolean cull;
    private boolean scissor;
    private boolean depthMask;
    private int touchedUnits;
    private int unpackBuffer;
    private boolean active;
    private boolean blendTouched;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private int blendEqRgb;
    private int blendEqAlpha;

    void capture() {
        drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        unpackBuffer = GL11.glGetInteger(0x88EF); // GL_PIXEL_UNPACK_BUFFER_BINDING
        viewportBuf.clear();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuf);
        for (int i = 0; i < 4; i++) {
            viewport[i] = viewportBuf.get(i);
        }
        maskBuf.clear();
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, maskBuf);
        for (int i = 0; i < 4; i++) {
            colorMask[i] = maskBuf.get(i) != 0;
        }
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        touchedUnits = 0;
        blendTouched = false;
        active = true;
        if (unpackBuffer != 0) {
            // texture allocation with a bound unpack buffer would read from it
            GL30.glBindBuffer(0x88EC, 0); // GL_PIXEL_UNPACK_BUFFER
        }
    }

    /** Call before binding anything on {@code unit}; leaves that unit active with no sampler object. */
    void touchUnit(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        if ((touchedUnits & (1 << unit)) != 0) {
            return;
        }
        touchedUnits |= 1 << unit;
        savedTexture[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        savedSampler[unit] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        if (savedSampler[unit] != 0) {
            GL33.glBindSampler(unit, 0);
        }
    }

    /** Call before changing the blend function or equation (particles); restored exactly, as Minecraft caches them. */
    void touchBlend() {
        if (blendTouched) {
            return;
        }
        blendTouched = true;
        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        blendEqRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        blendEqAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    }

    void restore() {
        if (!active) {
            return;
        }
        active = false;
        if (blendTouched) {
            GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            GL20.glBlendEquationSeparate(blendEqRgb, blendEqAlpha);
        }
        toggle(GL11.GL_DEPTH_TEST, depthTest);
        toggle(GL11.GL_BLEND, blend);
        toggle(GL11.GL_CULL_FACE, cull);
        toggle(GL11.GL_SCISSOR_TEST, scissor);
        GL11.glDepthMask(depthMask);
        GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao);
        for (int unit = 0; unit < UNITS; unit++) {
            if ((touchedUnits & (1 << unit)) != 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTexture[unit]);
                if (savedSampler[unit] != 0) {
                    GL33.glBindSampler(unit, savedSampler[unit]);
                }
            }
        }
        GL13.glActiveTexture(activeTexture);
        if (unpackBuffer != 0) {
            GL30.glBindBuffer(0x88EC, unpackBuffer);
        }
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    int touchedCount() {
        return Integer.bitCount(touchedUnits);
    }

    private static void toggle(int cap, boolean on) {
        if (on) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }
}
