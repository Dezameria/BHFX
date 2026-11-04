package net.deizyn.bhfx.impl.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.FxParticles;
import net.deizyn.bhfx.api.client.FxTexture;
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
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.ArrayList;
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
 *   <li>Particle batches are drawn into the chain at their order: the input is copied (or bent by the batches'
 *       distortion) into the next target, then the batches blend over it.</li>
 *   <li>Any GL failure disables post-processing for the session instead of breaking the game.</li>
 * </ul>
 */
public final class PostRenderer {
    private static final ResourceLocation COMPOSITE = BHFxMod.id("composite");
    private static final ResourceLocation BLOOM_DOWN = BHFxMod.id("bloom_down");
    private static final ResourceLocation BLOOM_UP = BHFxMod.id("bloom_up");
    private static final ResourceLocation PARTICLE_RESOLVE = BHFxMod.id("particle_resolve");
    private static final int BLOOM_LEVELS = 5;
    private static final int UNIT_INPUT = 0;
    private static final int UNIT_SCENE = 1;
    private static final int UNIT_DEPTH = 2;
    private static final int UNIT_AUX = 3;
    private static final int UNIT_BLOOM = 4;
    /** Pixels added around a bounded pass's rect (linear filtering and rounding at its edge). */
    private static final int RECT_PAD = 2;
    private static final float[] ZERO4 = new float[4];

    private final ShaderManager shaders = new ShaderManager();
    private final GlStateGuard guard = new GlStateGuard();
    private final FxTextures textures = new FxTextures();
    private final UniformBinder binder = new UniformBinder(guard, textures);
    private final ParticleRenderer particles = new ParticleRenderer(guard);
    private final List<PassListImpl.Entry> particleGroup = new ArrayList<>();
    private final GpuTimer timer = new GpuTimer();
    private final ResolutionController resolution = new ResolutionController();
    private final Matrix4f invProj = new Matrix4f();
    private final Matrix4f invView = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();
    private final Vector4f eyeClip = new Vector4f();
    private final Vector4f rectScratch = new Vector4f();
    private final Vector3f eye = new Vector3f();
    /** The terrain fog of this frame: rgba as the level shaders mix it, then start, end, shape (0 sphere, 1 cylinder). */
    private final float[] fogColor = new float[4];
    private final float[] fogRange = {Float.MAX_VALUE, Float.MAX_VALUE, 0f};
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
    private int distortFbo;
    private int distortTex;
    private int distortW;
    private int distortH;
    private Vec3 camera = Vec3.ZERO;

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
    private int lastSkippedCount;
    private int lastParticleCount;
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
        long bytes = (long) fullW * fullH * 4 + (long) fxW * fxH * (8 * 2 + 4)
                + (long) distortW * distortH * 4 + textures.heldBytes() + particles.heldBytes();
        if (bloomForW > 0) {
            for (int i = 0; i < BLOOM_LEVELS; i++) {
                bytes += (long) bloomSize[i][0] * bloomSize[i][1] * 8 * (i < BLOOM_LEVELS - 1 ? 2 : 1);
            }
        }
        return bytes;
    }

    public double resolutionShare() {
        return resolution.currentShare();
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
                + ", " + Math.round(resolution.currentShare() * 100) + "% budget"
                + (ms >= 0 ? String.format(", %.2f ms GPU", ms) : "")
                + ", ~" + (bytes >> 20) + " MB, " + shaders.compiledCount() + " shader(s)"
                + (lastSkippedCount > 0 ? ", " + lastSkippedCount + " off-screen pass(es) skipped" : "")
                + (lastParticleCount > 0 ? ", " + lastParticleCount + " particle slot(s)" : "")
                + (textures.count() > 0 ? ", " + textures.count() + " texture(s)" : "");
    }

    /** Drop compiled shaders and clear a previous failure; GL targets are rebuilt on demand. */
    public void reload() {
        RenderSystem.assertOnRenderThread();
        shaders.clear();
        textures.clear();
        failedSetups.clear();
        disabled = false;
        disabledReason = "";
    }

    /** Start loading a texture early (no GL needed). */
    public void preload(FxTexture texture) {
        textures.preload(texture);
    }

    public boolean textureReady(FxTexture texture) {
        return textures.ready(texture);
    }

    public void render(List<PassListImpl.Entry> passes, ScreenParamsImpl screen, Matrix4f projection, Matrix4f view,
                       Vec3 camera, float time, BHFxConfig.Quality quality) {
        this.camera = camera;
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
            int inputFbo = snapshotFbo;
            boolean inputFull = true;
            int ping = 0;
            int drawn = 0;
            int skipped = 0;
            boolean sceneLowReady = false;
            GL11.glViewport(0, 0, fxW, fxH);
            boolean bounds = BHFxConfig.PASS_BOUNDS.get();
            int particleBudget = particleCap(quality);
            int particleSlots = 0;
            for (int index = 0; index < passes.size(); index++) {
                PassListImpl.Entry pass = passes.get(index);
                if (pass.particles() != null) {
                    // a run of particle batches shares one copy of the input
                    int end = index + 1;
                    while (end < passes.size() && passes.get(end).particles() != null) {
                        end++;
                    }
                    particleGroup.clear();
                    boolean distort = false;
                    for (int j = index; j < end; j++) {
                        PassListImpl.Entry e = passes.get(j);
                        ParticleBatch b = e.particles();
                        if (b.closed() || b.filled > particleBudget - particleSlots
                                || shaders.getParticle(e.shader()) == null) {
                            skipped++;
                            continue;
                        }
                        ScreenRect r = e.radius() > 0
                                ? ScreenRect.ofSphere(viewProj, e.bx(), e.by(), e.bz(), e.radius(), rectScratch)
                                : ScreenRect.ofSphere(viewProj, b.ox - camera.x, b.oy - camera.y, b.oz - camera.z,
                                b.cullRadius(), rectScratch);
                        if (r.empty()) {
                            skipped++;
                            continue;
                        }
                        particleSlots += b.filled;
                        particleGroup.add(e);
                        distort |= b.blend == FxParticles.Blend.DISTORT;
                    }
                    index = end - 1;
                    if (particleGroup.isEmpty()) {
                        continue;
                    }
                    ShaderProgram resolve = distort ? shaders.get(PARTICLE_RESOLVE) : null;
                    if (resolve != null) {
                        // bend the input by the distortion batches while copying it into the next target
                        ensureDistort();
                        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, distortFbo);
                        GL30.glClearBufferfv(GL11.GL_COLOR, 0, ZERO4);
                        guard.touchBlend();
                        GL11.glEnable(GL11.GL_BLEND);
                        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
                        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
                        for (PassListImpl.Entry e : particleGroup) {
                            if (e.particles().blend == FxParticles.Blend.DISTORT) {
                                drawBatch(e, input, depth, time);
                            }
                        }
                        GL11.glDisable(GL11.GL_BLEND);
                        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, pingFbo[ping]);
                        begin(resolve, input, depth, fxW, fxH, time);
                        bindUnit(UNIT_AUX, distortTex, resolve, "AuxSampler");
                        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                    } else {
                        int from = inputFbo;
                        if (inputFull && (fxW != w || fxH != h)) {
                            if (!sceneLowReady) {
                                blit(snapshotFbo, sceneLowFbo, w, h, fxW, fxH, GL11.GL_LINEAR);
                                sceneLowReady = true;
                            }
                            from = sceneLowFbo;
                        }
                        blit(from, pingFbo[ping], fxW, fxH, fxW, fxH, GL11.GL_NEAREST);
                    }
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, pingFbo[ping]);
                    guard.touchBlend();
                    GL11.glEnable(GL11.GL_BLEND);
                    // premultiplied: ADD writes alpha 0 (pure add), ALPHA covers by its alpha; the target keeps its alpha
                    GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
                    GL14.glBlendEquation(GL14.GL_FUNC_ADD);
                    for (PassListImpl.Entry e : particleGroup) {
                        if (e.particles().blend != FxParticles.Blend.DISTORT) {
                            drawBatch(e, input, depth, time);
                        }
                    }
                    GL11.glDisable(GL11.GL_BLEND);
                    input = pingTex[ping];
                    inputFbo = pingFbo[ping];
                    inputFull = false;
                    ping ^= 1;
                    drawn++;
                    continue;
                }
                ShaderProgram program = shaders.get(pass.shader());
                if (program == null) {
                    continue;
                }
                ScreenRect rect = bounds && pass.radius() > 0
                        ? ScreenRect.ofSphere(viewProj, pass.bx(), pass.by(), pass.bz(), pass.radius(), rectScratch)
                        : ScreenRect.FULL;
                if (rect.empty()) {
                    skipped++;
                    continue;
                }
                boolean scissor = !rect.full();
                if (scissor) {
                    // the pass shades only its rect, so first make the rest of the target equal to the input. From
                    // the full-resolution snapshot, copy the composite's own low-res scene 1:1: a scaled blit straight
                    // into this float target samples rows slightly differently on some drivers, and the composite's
                    // detail restore (sharp / low-res ratio) turned that into bright horizontal lines on the ground
                    int from = inputFbo;
                    if (inputFull && (fxW != w || fxH != h)) {
                        if (!sceneLowReady) {
                            blit(snapshotFbo, sceneLowFbo, w, h, fxW, fxH, GL11.GL_LINEAR);
                            sceneLowReady = true;
                        }
                        from = sceneLowFbo;
                    }
                    blit(from, pingFbo[ping], fxW, fxH, fxW, fxH, GL11.GL_NEAREST);
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
                if (scissor) {
                    int x0 = Math.max(0, (int) Math.floor(rect.minU() * fxW) - RECT_PAD);
                    int y0 = Math.max(0, (int) Math.floor(rect.minV() * fxH) - RECT_PAD);
                    int x1 = Math.min(fxW, (int) Math.ceil(rect.maxU() * fxW) + RECT_PAD);
                    int y1 = Math.min(fxH, (int) Math.ceil(rect.maxV() * fxH) + RECT_PAD);
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                    GL11.glScissor(x0, y0, x1 - x0, y1 - y0);
                }
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                if (scissor) {
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                }
                input = pingTex[ping];
                inputFbo = pingFbo[ping];
                inputFull = false;
                ping ^= 1;
                drawn++;
            }
            lastPassCount = drawn;
            lastSkippedCount = skipped;
            lastParticleCount = particleSlots;
            particles.sweep(now);
            textures.sweep(now);

            int bloom = 0;
            if (drawn > 0 && quality.bloom && BHFxConfig.BLOOM.get() && screen.bloomStrength() > 0.001f) {
                bloom = bloom(input, depth, screen, time);
            }

            boolean upscaled = drawn > 0 && (fxW != w || fxH != h);
            if (upscaled && !sceneLowReady) {
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

    /** Like {@link #warmup}, for a particle material: one draw of one (empty, so invisible) instance. */
    public boolean warmupParticle(ResourceLocation id) {
        if (disabled) {
            return false;
        }
        RenderSystem.assertOnRenderThread();
        guard.capture();
        try {
            ShaderProgram program = shaders.getParticle(id);
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
                guard.touchUnit(unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            // no buffer texture bound: the slot reads life 0, so the vertex shader drops it
            guard.touchUnit(ParticleRenderer.UNIT_DATA);
            int data = program.location("PData");
            if (data >= 0) {
                // a samplerBuffer left on unit 0 would clash with the sampler2D there (invalid operation)
                GL20.glUniform1i(data, ParticleRenderer.UNIT_DATA);
            }
            org.lwjgl.opengl.GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, 1);
            return true;
        } catch (RuntimeException e) {
            BHFxMod.LOGGER.warn("bhfx warmup of particle material {} failed: {}", id, e.toString());
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

    /** Draw one particle batch into the bound target (blend state already set). */
    private void drawBatch(PassListImpl.Entry e, int input, int depth, float time) {
        ParticleBatch b = e.particles();
        ShaderProgram program = shaders.getParticle(e.shader());
        if (program == null) {
            return;
        }
        begin(program, input, depth, fxW, fxH, time);
        particles.bind(b, program);
        binder.f3("POrigin", (float) (b.ox - camera.x), (float) (b.oy - camera.y), (float) (b.oz - camera.z));
        binder.f1("PTime", e.time());
        binder.f4("PMotion", b.gx, b.gy, b.gz, b.drag);
        binder.f4("PShape", b.facing.ordinal(), b.stretch, b.pivot, 0f);
        binder.f4("PMode", b.blend.ordinal(), b.softness, b.distortion, 0f);
        binder.f3("POrbit", b.orbitX(), b.orbitY(), b.orbitZ());
        binder.f3("POrbitAxis", b.oax, b.oay, b.oaz);
        binder.f4("PTurb", b.turbAmp, b.turbFreq, b.turbScale, 0f);
        binder.f4("PRing", b.capacityValue(), b.filled, 0f, 0f);
        float colourMode = b.colorKeyCount;
        if (b.ramp != null) {
            // binding is what uploads a decoded texture (FxTextures.get); until then the 1x1 fallback is bound unused
            binder.texture("PRamp", b.ramp);
            if (textureReady(b.ramp)) {
                binder.f2("PRampRange", b.rampFrom, b.rampTo);
                colourMode = -1f;
            }
        }
        binder.f4("PCurves", colourMode, b.alphaKeyCount, b.sizeKeyCount, 0f);
        if (b.colorKeyCount > 0) {
            float[] k = b.colorKeys;
            binder.f3("PColorKey0", k[0], k[1], k[2]);
            binder.f3("PColorKey1", k[3], k[4], k[5]);
            binder.f3("PColorKey2", k[6], k[7], k[8]);
            binder.f3("PColorKey3", k[9], k[10], k[11]);
        }
        binder.f4("PAlphaKeys", b.alphaKeys[0], b.alphaKeys[1], b.alphaKeys[2], b.alphaKeys[3]);
        binder.f4("PSizeKeys", b.sizeKeys[0], b.sizeKeys[1], b.sizeKeys[2], b.sizeKeys[3]);
        try {
            e.setup().accept(binder);
        } catch (RuntimeException ex) {
            if (failedSetups.add(e.shader())) {
                BHFxMod.LOGGER.error("bhfx uniform setup for particle material {} threw", e.shader(), ex);
            }
            particles.unbind();
            return;
        }
        particles.draw(b);
        particles.unbind();
    }

    /** Most particle slots drawn per frame at this quality. */
    private static int particleCap(BHFxConfig.Quality quality) {
        return switch (quality) {
            case OFF -> 0;
            case LOW -> 4096;
            case MEDIUM -> 16384;
            case HIGH -> 32768;
        };
    }

    private void ensureDistort() {
        if (distortW == fxW && distortH == fxH && distortFbo != 0) {
            return;
        }
        guard.touchUnit(0);
        if (distortFbo == 0) {
            distortFbo = GL30.glGenFramebuffers();
            distortTex = GL11.glGenTextures();
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, distortTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG16F, fxW, fxH, 0, GL30.GL_RG, GL11.GL_FLOAT,
                (java.nio.ByteBuffer) null);
        attach(distortFbo, distortTex, "distortion");
        distortW = fxW;
        distortH = fxH;
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
        binder.f4("FogColor", fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
        binder.f4("FogRange", fogRange[0], fogRange[1], fogRange[2], 0f);
    }

    /**
     * Remembers the fog the terrain is drawn with, so passes can tell how much of a far pixel is fog (and swap it for
     * their own sky). Call while the terrain fog is set up (after the solid blocks).
     */
    public void captureFog() {
        float[] c = RenderSystem.getShaderFogColor();
        System.arraycopy(c, 0, fogColor, 0, Math.min(4, c.length));
        fogRange[0] = RenderSystem.getShaderFogStart();
        fogRange[1] = RenderSystem.getShaderFogEnd();
        fogRange[2] = RenderSystem.getShaderFogShape().getIndex();
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
        deleteFbo(distortFbo);
        deleteTex(distortTex);
        distortFbo = distortTex = distortW = distortH = 0;
        particles.releaseAll();
        textures.clear();
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
