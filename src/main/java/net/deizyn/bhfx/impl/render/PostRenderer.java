package net.deizyn.bhfx.impl.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.ImpactStyle;
import net.deizyn.bhfx.impl.BHFxConfig;
import net.deizyn.bhfx.impl.client.PassListImpl;
import net.deizyn.bhfx.impl.client.ScreenParamsImpl;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
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
 * Draws the frame's passes over the finished world, then bloom and the composite into Minecraft's main colour buffer.
 * <ul>
 *   <li>The world colour is copied once (the composite writes where it reads).</li>
 *   <li>Depth is sampled straight from Minecraft's depth texture — no bhfx framebuffer attaches it, so there is no
 *       feedback loop and no depth copy.</li>
 *   <li>Passes run in RGBA16F at a resolution capped by pixels and by measured GPU time; the composite tone-maps once
 *       and restores world detail when passes ran below native resolution.</li>
 *   <li>Bloom targets exist only while something uses them.</li>
 *   <li>Any GL failure disables post-processing for the session instead of breaking the game.</li>
 * </ul>
 */
public final class PostRenderer {
    private static final ResourceLocation COMPOSITE = BHFxMod.id("composite");
    private static final ResourceLocation BLOOM_DOWN = BHFxMod.id("bloom_down");
    private static final ResourceLocation BLOOM_UP = BHFxMod.id("bloom_up");
    private static final int BLOOM_LEVELS = 5;
    private static final int UNIT_INPUT = 0;
    private static final int UNIT_SCENE = 1;
    private static final int UNIT_DEPTH = 2;
    private static final int UNIT_AUX = 3;
    private static final int UNIT_BLOOM = 4;

    private final ShaderManager shaders = new ShaderManager();
    private final GlStateGuard guard = new GlStateGuard();
    private final UniformBinder binder = new UniformBinder();
    private final GpuTimer timer = new GpuTimer();
    private final ResolutionController resolution = new ResolutionController();
    private final Matrix4f invProj = new Matrix4f();
    private final Matrix4f invView = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();
    private final Vector4f eyeClip = new Vector4f();
    private final Vector3f eye = new Vector3f();
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

    private final int[] downFbo = new int[BLOOM_LEVELS];
    private final int[] downTex = new int[BLOOM_LEVELS];
    private final int[] upFbo = new int[BLOOM_LEVELS - 1];
    private final int[] upTex = new int[BLOOM_LEVELS - 1];
    private final int[][] bloomSize = new int[BLOOM_LEVELS][2];
    private int bloomForW;
    private int bloomForH;

    // the main target as last seen; a resize recreates its objects (often under the same names)
    private int mainFbo = -1;
    private int mainColor = -1;
    private int mainDepth = -1;
    private int mainW = -1;
    private int mainH = -1;

    private int warmVao;
    private int warmFbo;
    private int warmTex;

    private int lastPassCount;
    private double lastGpuSample = -1;
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

    /** Average GPU ms of recent measured frames, or -1. */
    public double averageGpuMs() {
        return resolution.averageMs();
    }

    /** True when resolution is at its floor and effects are still clearly over budget. */
    public boolean overBudget(double budgetMs) {
        return resolution.saturated(budgetMs);
    }

    /** GPU ms of a measurement that finished since the last call, or -1. For /bhfx stats. */
    public double takeGpuSample() {
        double ms = lastGpuSample;
        lastGpuSample = -1;
        return ms;
    }

    /** Bytes of GPU memory bhfx holds right now (estimate from target sizes). */
    public long heldBytes() {
        if (!allocated()) {
            return 0L;
        }
        long bytes = (long) fullW * fullH * 4 + (long) fxW * fxH * (8 * 2 + 4);
        if (bloomForW > 0) {
            for (int i = 0; i < BLOOM_LEVELS; i++) {
                bytes += (long) bloomSize[i][0] * bloomSize[i][1] * 8 * (i < BLOOM_LEVELS - 1 ? 2 : 1);
            }
        }
        return bytes;
    }

    public double resolutionShare() {
        return resolution.share();
    }

    /** Size of the effect targets, e.g. "2073x1109". */
    public String effectSize() {
        return fxW + "x" + fxH;
    }

    public void resetBudget() {
        resolution.reset();
    }

    public String describe() {
        if (disabled) {
            return "post DISABLED (" + disabledReason + ")";
        }
        if (!allocated()) {
            return "post idle (no GPU memory held)";
        }
        long bytes = heldBytes();
        double ms = resolution.averageMs();
        return "post " + fxW + "x" + fxH + " of " + fullW + "x" + fullH
                + ", " + Math.round(resolution.share() * 100) + "% budget"
                + (ms >= 0 ? String.format(", %.2f ms GPU", ms) : "")
                + ", ~" + (bytes >> 20) + " MB, " + shaders.compiledCount() + " shader(s)";
    }

    /** Drop compiled shaders and clear a previous failure; GL targets are rebuilt on demand. */
    public void reload() {
        RenderSystem.assertOnRenderThread();
        shaders.clear();
        failedSetups.clear();
        disabled = false;
        disabledReason = "";
    }

    public void render(List<PassListImpl.Entry> passes, ScreenParamsImpl screen, Matrix4f projection, Matrix4f view,
                       float time, BHFxConfig.Quality quality) {
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
        if (main.frameBufferId != mainFbo || color != mainColor || depth != mainDepth
                || main.width != mainW || main.height != mainH) {
            // Minecraft deleted and recreated its target (window resize): outFbo may still hold the deleted colour
            // texture even when the new one got the same id, so rebuild every target from scratch
            release();
            resolution.reset();
            mainFbo = main.frameBufferId;
            mainColor = color;
            mainDepth = depth;
            mainW = main.width;
            mainH = main.height;
        }
        long now = System.nanoTime();
        guard.capture();
        try {
            int w = main.width;
            int h = main.height;

            double ms = timer.poll();
            if (ms >= 0) {
                resolution.sample(ms, BHFxConfig.GPU_BUDGET_MS.get());
                lastGpuSample = ms;
            }
            boolean work = !passes.isEmpty();
            if (!work) {
                resolution.idle();
            }
            double cap = BHFxConfig.MAX_FX_MEGAPIXELS.get() * 1.0e6 * quality.pixelShare * resolution.share();
            double scale = Math.min(1.0, Math.sqrt(cap / ((double) w * h)));
            ensureCore();
            ensureTargets(w, h, Math.max(2, (int) (w * scale)), Math.max(2, (int) (h * scale)));
            attachOutput(color);
            if (work) {
                timer.begin();
            }

            blit(outFbo, snapshotFbo, w, h, w, h, GL11.GL_NEAREST);
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
            computeEye();

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

            int bloom = 0;
            if (drawn > 0 && quality.bloom && BHFxConfig.BLOOM.get() && screen.bloomStrength() > 0.001f) {
                bloom = bloom(input, depth, screen, time);
            }

            boolean upscaled = drawn > 0 && (fxW != w || fxH != h);
            if (upscaled) {
                blit(snapshotFbo, sceneLowFbo, w, h, fxW, fxH, GL11.GL_LINEAR);
            }

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, outFbo);
            GL11.glViewport(0, 0, w, h);
            ShaderProgram composite = shaders.get(COMPOSITE);
            if (composite != null) {
                composite(composite, input, depth, w, h, time, screen, upscaled, bloom);
            } else if (drawn > 0) {
                blit(pingFbo[ping ^ 1], outFbo, fxW, fxH, w, h, GL11.GL_LINEAR);
            }
            if (work) {
                timer.end();
            }

        } catch (RuntimeException e) {
            disable(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
        } finally {
            guard.restore();
        }
    }

    /**
     * Compile and link one pass, then draw it once into a 1x1 target: some drivers only finish compiling on the first
     * draw. Returns false when the shader is broken (it is logged and skipped like at runtime).
     */
    public boolean warmup(ResourceLocation id) {
        if (disabled) {
            return false;
        }
        RenderSystem.assertOnRenderThread();
        guard.capture();
        try {
            ShaderProgram program = shaders.get(id);
            if (program == null) {
                return false;
            }
            if (warmVao == 0) {
                warmVao = GL30.glGenVertexArrays();
                warmFbo = GL30.glGenFramebuffers();
                warmTex = GL11.glGenTextures();
                guard.touchUnit(0);
                texture(warmTex, 1, 1, GL30.GL_RGBA16F, GL11.GL_FLOAT);
                attach(warmFbo, warmTex, "warmup");
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, warmFbo);
            GL11.glViewport(0, 0, 1, 1);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL30.glBindVertexArray(warmVao);
            GL20.glUseProgram(program.id());
            for (int unit = UNIT_INPUT; unit <= UNIT_BLOOM; unit++) {
                // no texture: samples read zero, and nothing samples the target being drawn
                guard.touchUnit(unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            return true;
        } catch (RuntimeException e) {
            BHFxMod.LOGGER.warn("bhfx warmup of {} failed: {}", id, e.toString());
            return false;
        } finally {
            guard.restore();
        }
    }

    /** Free the 1x1 warmup target once every shader is warm. */
    public void endWarmup() {
        if (warmVao != 0) {
            RenderSystem.assertOnRenderThread();
            GL30.glDeleteVertexArrays(warmVao);
            deleteFbo(warmFbo);
            deleteTex(warmTex);
            warmVao = warmFbo = warmTex = 0;
        }
    }

    private void composite(ShaderProgram p, int input, int depth, int w, int h, float time, ScreenParamsImpl s,
                           boolean upscaled, int bloom) {
        begin(p, input, depth, w, h, time);
        bindUnit(UNIT_AUX, upscaled ? sceneLowTex : snapshotTex, p, "SceneLowSampler");
        bindUnit(UNIT_BLOOM, bloom != 0 ? bloom : snapshotTex, p, "BloomSampler");
        binder.f1("Upscaled", upscaled ? 1f : 0f);
        binder.f1("Bloom", bloom != 0 ? s.bloomStrength() : 0f);
        rgb(p, "Flash", s.flashRgb(), s.flashStrength());
        rgb(p, "Vignette", s.vignetteRgb(), s.vignetteStrength());
        binder.f1("Desaturate", s.desaturateValue());
        binder.f1("Aberration", s.aberrationValue());
        binder.f1("ZoomBlur", s.zoomBlurValue());
        ImpactStyle style = s.impactStyle();
        float impact = s.impactStrength();
        if (style != null && impact > 0.001f) {
            binder.f4("ImpactA", impact, style.kind().ordinal(), s.impactU(), s.impactV());
            rgb(p, "ImpactB", style.darkRgb(), style.focusLines());
            rgb(p, "ImpactC", style.lightRgb(), style.param());
        } else {
            binder.f4("ImpactA", 0f, 0f, 0.5f, 0.5f);
        }
        binder.f1("RippleCount", s.rippleCount());
        if (s.rippleCount() > 0) {
            binder.floats("RippleA", s.rippleA());
            binder.floats("RippleB", s.rippleB());
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /** Downsample the light the passes added into a 5-level pyramid and back up. Returns the result texture. */
    private int bloom(int source, int depth, ScreenParamsImpl screen, float time) {
        ShaderProgram down = shaders.get(BLOOM_DOWN);
        ShaderProgram up = shaders.get(BLOOM_UP);
        if (down == null || up == null) {
            return 0;
        }
        ensureBloom();
        int input = source;
        int inW = fxW;
        int inH = fxH;
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, downFbo[i]);
            GL11.glViewport(0, 0, bloomSize[i][0], bloomSize[i][1]);
            begin(down, input, depth, bloomSize[i][0], bloomSize[i][1], time);
            binder.f2("TexelSize", 1f / inW, 1f / inH);
            binder.f1("Prefilter", i == 0 ? 1f : 0f);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            input = downTex[i];
            inW = bloomSize[i][0];
            inH = bloomSize[i][1];
        }
        float radius = screen.bloomRadius();
        for (int i = BLOOM_LEVELS - 2; i >= 0; i--) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, upFbo[i]);
            GL11.glViewport(0, 0, bloomSize[i][0], bloomSize[i][1]);
            begin(up, input, depth, bloomSize[i][0], bloomSize[i][1], time);
            bindUnit(UNIT_AUX, downTex[i], up, "AuxSampler");
            binder.f2("TexelSize", 1f / inW, 1f / inH);
            binder.f1("Radius", radius);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            input = upTex[i];
            inW = bloomSize[i][0];
            inH = bloomSize[i][1];
        }
        return upTex[0];
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
        binder.f3("Eye", eye.x, eye.y, eye.z);
    }

    /**
     * Where rays really start, camera-relative. Minecraft folds view bobbing (and the hurt tilt) into the projection
     * matrix, so while walking the eye sits a little off the origin. The projection maps its centre to (0, 0, k, 0),
     * so the inverse of that direction is the eye.
     */
    private void computeEye() {
        invProj.transform(eyeClip.set(0f, 0f, 1f, 0f));
        if (Math.abs(eyeClip.w) < 1e-12f) {
            eye.zero();
            return;
        }
        invView.transformPosition(eye.set(eyeClip.x / eyeClip.w, eyeClip.y / eyeClip.w, eyeClip.z / eyeClip.w));
    }

    private void bindUnit(int unit, int texture, ShaderProgram program, String sampler) {
        guard.touchUnit(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        int loc = program.location(sampler);
        if (loc >= 0) {
            GL20.glUniform1i(loc, unit);
        }
    }

    /** rgb as 0..1 (display space) plus a fourth value. */
    private void rgb(ShaderProgram p, String name, int rgb, float w) {
        binder.f4(name, ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, w);
    }

    private void setMatrix(int location, Matrix4f matrix) {
        if (location >= 0) {
            matrixBuf.clear();
            matrix.get(matrixBuf);
            GL20.glUniformMatrix4fv(location, false, matrixBuf);
        }
    }

    private static void blit(int from, int to, int fw, int fh, int tw, int th, int filter) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to);
        GL30.glBlitFramebuffer(0, 0, fw, fh, 0, 0, tw, th, GL11.GL_COLOR_BUFFER_BIT, filter);
    }

    // ------------------------------------------------------------------ targets

    private void ensureCore() {
        if (vao != 0) {
            return;
        }
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

    private void ensureTargets(int w, int h, int fw, int fh) {
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

    private void ensureBloom() {
        if (bloomForW == fxW && bloomForH == fxH) {
            return;
        }
        guard.touchUnit(0);
        if (downFbo[0] == 0) {
            for (int i = 0; i < BLOOM_LEVELS; i++) {
                downFbo[i] = GL30.glGenFramebuffers();
                downTex[i] = GL11.glGenTextures();
            }
            for (int i = 0; i < BLOOM_LEVELS - 1; i++) {
                upFbo[i] = GL30.glGenFramebuffers();
                upTex[i] = GL11.glGenTextures();
            }
        }
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            int bw = Math.max(1, fxW >> (i + 1));
            int bh = Math.max(1, fxH >> (i + 1));
            bloomSize[i][0] = bw;
            bloomSize[i][1] = bh;
            texture(downTex[i], bw, bh, GL30.GL_RGBA16F, GL11.GL_FLOAT);
            attach(downFbo[i], downTex[i], "bloom");
            if (i < BLOOM_LEVELS - 1) {
                texture(upTex[i], bw, bh, GL30.GL_RGBA16F, GL11.GL_FLOAT);
                attach(upFbo[i], upTex[i], "bloom");
            }
        }
        bloomForW = fxW;
        bloomForH = fxH;
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
        deleteFbo(outFbo);
        deleteFbo(snapshotFbo);
        deleteFbo(sceneLowFbo);
        GL11.glDeleteTextures(snapshotTex);
        GL11.glDeleteTextures(sceneLowTex);
        for (int i = 0; i < 2; i++) {
            deleteFbo(pingFbo[i]);
            GL11.glDeleteTextures(pingTex[i]);
            pingFbo[i] = pingTex[i] = 0;
        }
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            deleteFbo(downFbo[i]);
            deleteTex(downTex[i]);
            downFbo[i] = downTex[i] = 0;
        }
        for (int i = 0; i < BLOOM_LEVELS - 1; i++) {
            deleteFbo(upFbo[i]);
            deleteTex(upTex[i]);
            upFbo[i] = upTex[i] = 0;
        }
        timer.delete();
        vao = outFbo = snapshotFbo = snapshotTex = sceneLowFbo = sceneLowTex = 0;
        outAttached = fullW = fullH = fxW = fxH = bloomForW = bloomForH = 0;
        BHFxMod.LOGGER.debug("bhfx released its GPU targets");
    }

    private static void deleteFbo(int id) {
        if (id != 0) {
            GL30.glDeleteFramebuffers(id);
        }
    }

    private static void deleteTex(int id) {
        if (id != 0) {
            GL11.glDeleteTextures(id);
        }
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
