package net.offkung.bhfx.impl.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.offkung.bhfx.BHFxMod;
import net.offkung.bhfx.impl.BHFxConfig;
import net.offkung.bhfx.impl.client.PassListImpl;
import net.offkung.bhfx.impl.client.ScreenParamsImpl;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws the frame's passes over the finished world, then the composite into Minecraft's main colour buffer.
 * <ul>
 *   <li>The world colour is copied once (the composite writes where it reads).</li>
 *   <li>Depth is sampled straight from Minecraft's depth texture — none of bhfx's framebuffers attach it, so there is
 *       no feedback loop and no depth copy.</li>
 *   <li>Passes run at a capped resolution in RGBA16F (HDR between passes); the composite tone-maps once and restores
 *       world detail when passes ran below native resolution.</li>
 *   <li>Any GL failure disables post-processing for the session instead of breaking the game.</li>
 * </ul>
 */
public final class PostRenderer {
    private static final ResourceLocation COMPOSITE = BHFxMod.id("composite");
    private static final int UNIT_INPUT = 0;
    private static final int UNIT_SCENE = 1;
    private static final int UNIT_DEPTH = 2;
    private static final int UNIT_SCENE_LOW = 3;

    private final ShaderManager shaders = new ShaderManager();
    private final GlStateGuard guard = new GlStateGuard();
    private final UniformBinder binder = new UniformBinder(guard);
    private final Matrix4f invProj = new Matrix4f();
    private final Matrix4f invView = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();
    private final FloatBuffer matrixBuf = BufferUtils.createFloatBuffer(16);
    private final Set<ResourceLocation> failedSetups = new HashSet<>();

    private int vao;
    private int outFbo;
    private int outAttached;
    private int snapshotFbo;
    private int snapshotTex;
    private int sceneLowFbo;
    private int sceneLowTex;
    private final int[] pingFbo = new int[2];
    private final int[] pingTex = new int[2];
    private int fullW;
    private int fullH;
    private int fxW;
    private int fxH;
    private int lastPassCount;
    private boolean disabled;
    private String disabledReason = "";

    public boolean disabled() {
        return disabled;
    }

    public boolean allocated() {
        return vao != 0;
    }

    public int lastPassCount() {
        return lastPassCount;
    }

    public String describe() {
        if (disabled) {
            return "post DISABLED (" + disabledReason + ")";
        }
        if (!allocated()) {
            return "post idle (no GPU memory held)";
        }
        long bytes = (long) fullW * fullH * 4 + (long) fxW * fxH * (8 * 2 + 4);
        return "post " + fxW + "x" + fxH + " of " + fullW + "x" + fullH + ", ~" + (bytes >> 20) + " MB, "
                + shaders.compiledCount() + " shader(s)";
    }

    /** Drop compiled shaders and clear a previous failure; GL targets are rebuilt on demand. */
    public void reload() {
        RenderSystem.assertOnRenderThread();
        shaders.clear();
        failedSetups.clear();
        disabled = false;
        disabledReason = "";
    }

    public void render(List<PassListImpl.Entry> passes, ScreenParamsImpl screen, Matrix4f projection, Matrix4f view, float time) {
        if (disabled) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int color = main.getColorTextureId();
        int depth = main.getDepthTextureId();
        if (color <= 0 || depth <= 0 || main.width <= 0 || main.height <= 0) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        guard.capture();
        try {
            int w = main.width;
            int h = main.height;
            double cap = BHFxConfig.MAX_FX_MEGAPIXELS.get() * 1.0e6 * BHFxConfig.QUALITY.get().pixelShare;
            double scale = Math.min(1.0, Math.sqrt(cap / ((double) w * h)));
            ensureTargets(w, h, Math.max(2, (int) (w * scale)), Math.max(2, (int) (h * scale)));
            attachOutput(color);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, outFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshotFbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL30.glBindVertexArray(vao);
            invProj.set(projection).invert();
            invView.set(view).invert();
            viewProj.set(projection).mul(view);

            int input = snapshotTex;
            int ping = 0;
            int drawn = 0;
            GL11.glViewport(0, 0, fxW, fxH);
            for (PassListImpl.Entry pass : passes) {
                ShaderProgram program = shaders.get(pass.shader());
                if (program == null) {
                    continue;
                }
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, pingFbo[ping]);
                begin(program, input, depth, fxW, fxH, time);
                try {
                    pass.setup().accept(binder);
                } catch (RuntimeException e) {
                    if (failedSetups.add(pass.shader())) {
                        BHFxMod.LOGGER.error("bhfx uniform setup for {} threw", pass.shader(), e);
                    }
                    continue;
                }
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                input = pingTex[ping];
                ping ^= 1;
                drawn++;
            }
            lastPassCount = drawn;

            boolean upscaled = drawn > 0 && (fxW != w || fxH != h);
            if (upscaled) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshotFbo);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneLowFbo);
                GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, fxW, fxH, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            }

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, outFbo);
            GL11.glViewport(0, 0, w, h);
            ShaderProgram composite = shaders.get(COMPOSITE);
            if (composite != null) {
                begin(composite, input, depth, w, h, time);
                bindUnit(UNIT_SCENE_LOW, upscaled ? sceneLowTex : snapshotTex, composite, "SceneLowSampler");
                binder.f1("Upscaled", upscaled ? 1f : 0f);
                int fr = screen.flashRgb();
                binder.f4("Flash", ((fr >> 16) & 0xFF) / 255f, ((fr >> 8) & 0xFF) / 255f, (fr & 0xFF) / 255f,
                        screen.flashStrength(BHFxConfig.FLASH_SCALE.get()));
                int vr = screen.vignetteRgb();
                binder.f4("Vignette", ((vr >> 16) & 0xFF) / 255f, ((vr >> 8) & 0xFF) / 255f, (vr & 0xFF) / 255f,
                        screen.vignetteStrength());
                binder.f1("Desaturate", screen.desaturateValue());
                binder.f1("Aberration", screen.aberrationValue());
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            } else if (drawn > 0) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, pingFbo[ping ^ 1]);
                GL30.glBlitFramebuffer(0, 0, fxW, fxH, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            }
        } catch (RuntimeException e) {
            disable(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
        } finally {
            guard.restore();
        }
        binder.loadPendingTextures();
    }

    private void begin(ShaderProgram program, int input, int depth, int outW, int outH, float time) {
        GL20.glUseProgram(program.id());
        binder.begin(program);
        bindUnit(UNIT_INPUT, input, program, "DiffuseSampler");
        bindUnit(UNIT_SCENE, snapshotTex, program, "SceneSampler");
        bindUnit(UNIT_DEPTH, depth, program, "DepthSampler");
        setMatrix(program.location("InvProj"), invProj);
        setMatrix(program.location("InvView"), invView);
        setMatrix(program.location("ViewProj"), viewProj);
        binder.f2("OutSize", outW, outH);
        binder.f2("ScreenSize", fullW, fullH);
        binder.f1("GameTime", time);
    }

    private void bindUnit(int unit, int texture, ShaderProgram program, String sampler) {
        guard.touchUnit(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        int loc = program.location(sampler);
        if (loc >= 0) {
            GL20.glUniform1i(loc, unit);
        }
    }

    private void setMatrix(int location, Matrix4f matrix) {
        if (location >= 0) {
            matrixBuf.clear();
            matrix.get(matrixBuf);
            GL20.glUniformMatrix4fv(location, false, matrixBuf);
        }
    }

    // ------------------------------------------------------------------ targets

    private void ensureTargets(int w, int h, int fw, int fh) {
        if (vao == 0) {
            vao = GL30.glGenVertexArrays();
            outFbo = GL30.glGenFramebuffers();
            snapshotFbo = GL30.glGenFramebuffers();
            snapshotTex = GL11.glGenTextures();
            sceneLowFbo = GL30.glGenFramebuffers();
            sceneLowTex = GL11.glGenTextures();
            for (int i = 0; i < 2; i++) {
                pingFbo[i] = GL30.glGenFramebuffers();
                pingTex[i] = GL11.glGenTextures();
            }
            outAttached = 0;
        }
        guard.touchUnit(0);
        // full-resolution and effect-resolution targets are rebuilt independently
        if (w != fullW || h != fullH) {
            fullW = w;
            fullH = h;
            texture(snapshotTex, w, h, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
            attach(snapshotFbo, snapshotTex, "snapshot");
        }
        if (fw != fxW || fh != fxH) {
            fxW = fw;
            fxH = fh;
            for (int i = 0; i < 2; i++) {
                texture(pingTex[i], fw, fh, GL30.GL_RGBA16F, GL11.GL_FLOAT);
                attach(pingFbo[i], pingTex[i], "pass");
            }
            texture(sceneLowTex, fw, fh, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
            attach(sceneLowFbo, sceneLowTex, "scene-low");
        }
    }

    private void attachOutput(int color) {
        if (color != outAttached) {
            attach(outFbo, color, "output");
            outAttached = color;
        }
    }

    private static void texture(int id, int w, int h, int internalFormat, int type) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, w, h, 0, GL11.GL_RGBA, type, (java.nio.ByteBuffer) null);
    }

    private static void attach(int fbo, int texture, String label) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("incomplete " + label + " framebuffer (0x" + Integer.toHexString(status) + ")");
        }
    }

    /** Free every GL object; called after 30 s without effects. Rebuilt on the next frame that needs it. */
    public void release() {
        RenderSystem.assertOnRenderThread();
        if (vao == 0) {
            return;
        }
        GL30.glDeleteVertexArrays(vao);
        GL30.glDeleteFramebuffers(outFbo);
        GL30.glDeleteFramebuffers(snapshotFbo);
        GL30.glDeleteFramebuffers(sceneLowFbo);
        GL11.glDeleteTextures(snapshotTex);
        GL11.glDeleteTextures(sceneLowTex);
        for (int i = 0; i < 2; i++) {
            GL30.glDeleteFramebuffers(pingFbo[i]);
            GL11.glDeleteTextures(pingTex[i]);
            pingFbo[i] = 0;
            pingTex[i] = 0;
        }
        vao = outFbo = snapshotFbo = snapshotTex = sceneLowFbo = sceneLowTex = 0;
        outAttached = fullW = fullH = fxW = fxH = 0;
        BHFxMod.LOGGER.debug("bhfx released its GPU targets");
    }

    private void disable(String reason, Throwable cause) {
        disabled = true;
        disabledReason = reason;
        BHFxMod.LOGGER.error("bhfx post-processing disabled for this session: {}", reason, cause);
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(Component.literal("[bhfx] post effects turned off after a graphics error. "
                    + "Spells still work. /bhfx reload to retry.").withStyle(ChatFormatting.GOLD));
        }
    }
}
