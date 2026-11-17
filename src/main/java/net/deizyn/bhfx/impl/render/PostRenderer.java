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
import net.deizyn.bhfx.api.client.FxPose;
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
    private static final ResourceLocation LAYER_RESOLVE = BHFxMod.id("layer_resolve");
    private static final int BLOOM_LEVELS = 5;
    private static final int UNIT_INPUT = 0;
    private static final int UNIT_SCENE = 1;
    private static final int UNIT_DEPTH = 2;
    private static final int UNIT_AUX = 3;
    private static final int UNIT_BLOOM = 4;
    /** Pixels added around a bounded pass's rect (linear filtering and rounding at its edge). */
    private static final int RECT_PAD = 2;
    private static final float[] ZERO4 = new float[4];
    private static final float[] ONE1 = {1f};

    private final ShaderManager shaders = new ShaderManager();
    private final GlStateGuard guard = new GlStateGuard();
    private final FxTextures textures = new FxTextures();
    private final DataTables tables = new DataTables(guard);
    private final UniformBinder binder = new UniformBinder(guard, textures, tables);
    private final ParticleRenderer particles = new ParticleRenderer(guard);
    private final FxModels models = new FxModels();
    private final List<PassListImpl.Entry> modelGroup = new ArrayList<>();
    private final Set<ResourceLocation> failedModelSetups = new HashSet<>();
    /** Models draw into the ping targets with a depth buffer of their own (the world's is only sampled). */
    private final int[] modelFbo = new int[2];
    private final int[] modelColor = new int[2];
    private int modelDepth;
    private int modelW;
    private int modelH;
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
    /** Target of low-resolution layer passes (PassOptions.halfRes), RGBA16F. */
    private int layerFbo;
    private int layerTex;
    private int layerW;
    private int layerH;
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
    private int lastCopyCount;
    private int lastIdleBatches;

    /** Where the last frame's CPU time went, in nanoseconds; see {@link #PHASE_NAMES}. */
    public static final String[] PHASE_NAMES = {"stride", "particle upload", "particle uniforms", "particle draw",
            "passes+models+copies", "sweep", "bloom", "composite", "frame setup"};
    private static final int PH_STRIDE = 0, PH_UPLOAD = 1, PH_UNIFORM = 2, PH_PDRAW = 3, PH_LOOP = 4, PH_SWEEP = 5,
            PH_BLOOM = 6, PH_POST = 7, PH_SETUP = 8;
    private final long[] phases = new long[PHASE_NAMES.length];

    public long[] phases() {
        return phases;
    }
    /** Every non-ribbon batch draws one slot in this many when the frame wants more slots than the cap (1 = all). */
    private int particleStride = 1;
    private int strideLowFrames;
    /** Frames the wanted stride must stay lower before the stride drops, so it doesn't swing frame to frame. */
    private static final int STRIDE_SETTLE_FRAMES = 30;
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
                + (long) distortW * distortH * 4 + (long) layerW * layerH * 8 + textures.heldBytes() + particles.heldBytes()
                + tables.heldBytes() + models.heldBytes() + (modelDepth != 0 ? (long) modelW * modelH * 4 : 0L);
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
                + ", " + lastCopyCount + " full copy(ies)"
                + (lastIdleBatches > 0 ? ", " + lastIdleBatches + " empty batch(es) not drawn" : "")
                + (particleStride > 1 ? ", particles thinned to 1 in " + particleStride : "")
                + (textures.count() > 0 ? ", " + textures.count() + " texture(s)" : "")
                + (tables.count() > 0 ? ", " + tables.count() + " data table(s)" : "")
                + (models.count() > 0 ? ", " + models.count() + " model(s)" : "");
    }

    /** Drop compiled shaders and clear a previous failure; GL targets are rebuilt on demand. */
    public void reload() {
        RenderSystem.assertOnRenderThread();
        shaders.clear();
        textures.clear();
        models.clear();
        failedSetups.clear();
        failedModelSetups.clear();
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
        java.util.Arrays.fill(phases, 0L);
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
            // where the ping target that is not the input differs from the input: everywhere, or only inside the
            // rect the last bounded pass shaded (then the next bounded pass copies just that rect)
            boolean otherAll = true;
            int ox0 = 0, oy0 = 0, ox1 = 0, oy1 = 0;
            int copies = 0;
            int idle = 0;
            GL11.glViewport(0, 0, fxW, fxH);
            boolean bounds = BHFxConfig.PASS_BOUNDS.get();
            int particleBudget = particleCap(quality);
            int particleSlots = 0;
            long t1 = System.nanoTime();
            phases[PH_SETUP] = t1 - now;
            updateParticleStride(passes, particleBudget);
            long t2 = System.nanoTime();
            phases[PH_STRIDE] = t2 - t1;
            int ribbonDrawn = 0;
            for (int index = 0; index < passes.size(); index++) {
                PassListImpl.Entry pass = passes.get(index);
                if (pass.model() != null) {
                    // a run of models shares one copy of the input and one depth buffer
                    // A material that reads the image (bhfx:distort) must see the models drawn before it, so it ends
                    // the run it would otherwise join; several such materials in a row share one run.
                    int end = index + 1;
                    boolean plainSeen = !readsImage(pass);
                    while (end < passes.size() && passes.get(end).model() != null) {
                        boolean reads = readsImage(passes.get(end));
                        if (reads && plainSeen) {
                            break;
                        }
                        plainSeen |= !reads;
                        end++;
                    }
                    modelGroup.clear();
                    for (int j = index; j < end; j++) {
                        PassListImpl.Entry e = passes.get(j);
                        FxModels.Mesh mesh = models.get(e.model().model().file(), now);
                        if (mesh == null || shaders.getModel(e.shader()) == null) {
                            skipped++;
                            continue;
                        }
                        ScreenRect r = e.radius() > 0
                                ? ScreenRect.ofSphere(viewProj, e.bx(), e.by(), e.bz(), e.radius(), rectScratch)
                                : e.model().pieces() != null ? piecesRect(e.model(), mesh)
                                : e.model().instances() != null ? ScreenRect.FULL : modelRect(e.model(), mesh);
                        if (r.empty()) {
                            skipped++;
                            continue;
                        }
                        if (e.model().pieces() != null) {
                            particleSlots += instances(e.model().pieces()); // pieces are particle slots too
                        }
                        modelGroup.add(e);
                    }
                    index = end - 1;
                    if (modelGroup.isEmpty()) {
                        continue;
                    }
                    // in place: the input is already an effect target and no material here reads it, so draw onto it
                    // instead of copying it first
                    boolean inPlace = !inputFull;
                    for (int j = 0; inPlace && j < modelGroup.size(); j++) {
                        inPlace = shaders.getModel(modelGroup.get(j).shader()).location("DiffuseSampler") < 0;
                    }
                    int target = inPlace ? ping ^ 1 : ping;
                    if (!inPlace) {
                        int from = inputFbo;
                        if (inputFull && (fxW != w || fxH != h)) {
                            if (!sceneLowReady) {
                                blit(snapshotFbo, sceneLowFbo, w, h, fxW, fxH, GL11.GL_LINEAR);
                                sceneLowReady = true;
                            }
                            from = sceneLowFbo;
                        }
                        blit(from, pingFbo[ping], fxW, fxH, fxW, fxH, GL11.GL_NEAREST);
                        copies++;
                    }
                    otherAll = true;
                    ensureModelTargets();
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, modelFbo[target]);
                    int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
                    GL11.glDepthMask(true);
                    GL30.glClearBufferfv(GL11.GL_DEPTH, 0, ONE1);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GL11.glDepthFunc(GL11.GL_LEQUAL);
                    GL11.glEnable(GL11.GL_CULL_FACE);
                    guard.touchBlend();
                    GL11.glEnable(GL11.GL_BLEND);
                    // premultiplied, like particles: BHFX_ADD writes alpha 0 (pure add); the target keeps its alpha
                    GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
                    GL14.glBlendEquation(GL14.GL_FUNC_ADD);
                    for (PassListImpl.Entry e : modelGroup) {
                        drawModel(e, inPlace ? snapshotTex : input, depth, time, now);
                    }
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glDisable(GL11.GL_CULL_FACE);
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    GL11.glDepthFunc(depthFunc);
                    GL11.glDepthMask(false);
                    GL30.glBindVertexArray(vao);
                    if (!inPlace) {
                        input = pingTex[ping];
                        inputFbo = pingFbo[ping];
                        inputFull = false;
                        ping ^= 1;
                    }
                    drawn++;
                    continue;
                }
                if (pass.particles() != null) {
                    // a run of particle batches shares one copy of the input
                    int end = index + 1;
                    while (end < passes.size() && passes.get(end).particles() != null && passes.get(end).model() == null) {
                        end++;
                    }
                    particleGroup.clear();
                    boolean distort = false;
                    for (int j = index; j < end; j++) {
                        PassListImpl.Entry e = passes.get(j);
                        ParticleBatch b = e.particles();
                        boolean ribbon = b.facing == FxParticles.Facing.RIBBON;
                        if (b.live == 0 && !b.closed()) {
                            idle++; // nothing alive or waiting to be born: no state, no draw call
                            continue;
                        }
                        if (b.closed() || shaders.getParticle(e.shader()) == null
                                || (ribbon && ribbonDrawn + b.live > particleBudget)) {
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
                        particleSlots += instances(b);
                        if (ribbon) {
                            ribbonDrawn += b.live;
                        }
                        particleGroup.add(e);
                        distort |= b.blend == FxParticles.Blend.DISTORT;
                    }
                    index = end - 1;
                    if (particleGroup.isEmpty()) {
                        continue;
                    }
                    ShaderProgram resolve = distort ? shaders.get(PARTICLE_RESOLVE) : null;
                    // in place, as for models: nothing here bends or reads the input
                    boolean inPlace = !inputFull && resolve == null;
                    for (int j = 0; inPlace && j < particleGroup.size(); j++) {
                        inPlace = shaders.getParticle(particleGroup.get(j).shader()).location("DiffuseSampler") < 0;
                    }
                    int target = inPlace ? ping ^ 1 : ping;
                    otherAll = true;
                    if (inPlace) {
                        // nothing to copy
                    } else if (resolve != null) {
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
                        copies++;
                    }
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, pingFbo[target]);
                    guard.touchBlend();
                    GL11.glEnable(GL11.GL_BLEND);
                    // premultiplied: ADD writes alpha 0 (pure add), ALPHA covers by its alpha; the target keeps its alpha
                    GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
                    GL14.glBlendEquation(GL14.GL_FUNC_ADD);
                    for (PassListImpl.Entry e : particleGroup) {
                        if (e.particles().blend != FxParticles.Blend.DISTORT) {
                            drawBatch(e, inPlace ? snapshotTex : input, depth, time);
                        }
                    }
                    GL11.glDisable(GL11.GL_BLEND);
                    if (!inPlace) {
                        input = pingTex[ping];
                        inputFbo = pingFbo[ping];
                        inputFull = false;
                        ping ^= 1;
                    }
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
                ShaderProgram resolve = pass.layer() > 0f ? shaders.get(LAYER_RESOLVE) : null;
                if (resolve != null) {
                    // a layer: draw the pass small into its own target, then the pass proper is the upsample over the image
                    ensureLayer(pass.layer());
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, layerFbo);
                    GL11.glViewport(0, 0, layerW, layerH);
                    if (scissor) {
                        int lx0 = Math.max(0, (int) Math.floor(rect.minU() * layerW) - RECT_PAD);
                        int ly0 = Math.max(0, (int) Math.floor(rect.minV() * layerH) - RECT_PAD);
                        int lx1 = Math.min(layerW, (int) Math.ceil(rect.maxU() * layerW) + RECT_PAD);
                        int ly1 = Math.min(layerH, (int) Math.ceil(rect.maxV() * layerH) + RECT_PAD);
                        GL11.glEnable(GL11.GL_SCISSOR_TEST);
                        GL11.glScissor(lx0, ly0, lx1 - lx0, ly1 - ly0);
                    }
                    GL30.glClearBufferfv(GL11.GL_COLOR, 0, ZERO4);
                    begin(program, input, depth, layerW, layerH, time);
                    boolean ok = true;
                    try {
                        pass.setup().accept(binder);
                    } catch (RuntimeException e) {
                        ok = false;
                        if (failedSetups.add(pass.shader())) {
                            BHFxMod.LOGGER.error("bhfx uniform setup for {} threw", pass.shader(), e);
                        }
                    }
                    if (ok) {
                        binder.f1("Presence", pass.presence());
                        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                    }
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL11.glViewport(0, 0, fxW, fxH);
                    if (!ok) {
                        continue;
                    }
                    program = resolve;
                }
                if (scissor) {
                    // the pass shades only its rect, so first make the rest of the target equal to the input. From
                    // the full-resolution snapshot, copy the composite's own low-res scene 1:1: a scaled blit straight
                    // into this float target samples rows slightly differently on some drivers, and the composite's
                    // detail restore (sharp / low-res ratio) turned that into bright horizontal lines on the ground
                    if (!otherAll && !inputFull) {
                        // this target is the input from two passes back: it differs only where the last pass shaded
                        if (ox1 > ox0 && oy1 > oy0) {
                            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, inputFbo);
                            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, pingFbo[ping]);
                            GL30.glBlitFramebuffer(ox0, oy0, ox1, oy1, ox0, oy0, ox1, oy1, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
                        }
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
                        copies++;
                    }
                    // equal to the input now; a pass that fails below leaves it so
                    otherAll = false;
                    ox0 = oy0 = ox1 = oy1 = 0;
                }
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, pingFbo[ping]);
                begin(program, input, depth, fxW, fxH, time);
                if (resolve != null) {
                    bindUnit(UNIT_AUX, layerTex, program, "AuxSampler");
                    binder.f2("LayerSize", layerW, layerH);
                } else {
                    try {
                        pass.setup().accept(binder);
                    } catch (RuntimeException e) {
                        if (failedSetups.add(pass.shader())) {
                            BHFxMod.LOGGER.error("bhfx uniform setup for {} threw", pass.shader(), e);
                        }
                        continue;
                    }
                    binder.f1("Presence", pass.presence());
                }
                if (scissor) {
                    int x0 = Math.max(0, (int) Math.floor(rect.minU() * fxW) - RECT_PAD);
                    int y0 = Math.max(0, (int) Math.floor(rect.minV() * fxH) - RECT_PAD);
                    int x1 = Math.min(fxW, (int) Math.ceil(rect.maxU() * fxW) + RECT_PAD);
                    int y1 = Math.min(fxH, (int) Math.ceil(rect.maxV() * fxH) + RECT_PAD);
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                    GL11.glScissor(x0, y0, x1 - x0, y1 - y0);
                    // the old input becomes the other target: it lacks only this rect (when it was an effect target)
                    otherAll = inputFull;
                    ox0 = x0;
                    oy0 = y0;
                    ox1 = x1;
                    oy1 = y1;
                } else {
                    otherAll = true;
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
            lastCopyCount = copies;
            lastIdleBatches = idle;
            long t3 = System.nanoTime();
            phases[PH_LOOP] = t3 - t2 - phases[PH_UPLOAD] - phases[PH_UNIFORM] - phases[PH_PDRAW];
            particles.sweep(now);
            textures.sweep(now);
            tables.sweep(now);
            models.sweep(now);
            long t4 = System.nanoTime();
            phases[PH_SWEEP] = t4 - t3;

            int bloom = 0;
            if (drawn > 0 && quality.bloom && BHFxConfig.BLOOM.get() && screen.bloomStrength() > 0.001f) {
                bloom = bloom(input, depth, screen, time);
            }

            long t5 = System.nanoTime();
            phases[PH_BLOOM] = t5 - t4;
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
            phases[PH_POST] = System.nanoTime() - t5;

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
            binder.begin(program); // samplerBuffers off unit 0 (two sampler types on one unit fail the draw)
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

    /** Compile and link a model material (models have no shared geometry to draw it with here). */
    public boolean warmupModel(ResourceLocation id) {
        if (disabled) {
            return false;
        }
        RenderSystem.assertOnRenderThread();
        return shaders.getModel(id) != null;
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
            binder.begin(program); // samplerBuffers off unit 0 (two sampler types on one unit fail the draw)
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

    /** Screen rect of every piece of a particle-driven model: the batch's own sphere grown by the model's size. */
    private ScreenRect piecesRect(ModelDraw md, FxModels.Mesh mesh) {
        ParticleBatch b = md.pieces();
        float[] lo = mesh.min();
        float[] hi = mesh.max();
        float reach = 0f;
        for (int i = 0; i < 3; i++) {
            reach = Math.max(reach, Math.max(Math.abs(lo[i]), Math.abs(hi[i])));
        }
        // sizes scale the model: the cull radius already holds each particle's size once, so this over-covers a little
        double r = b.cullRadius() * (1.0 + reach * 1.75 * md.scale());
        return ScreenRect.ofSphere(viewProj, b.ox - camera.x, b.oy - camera.y, b.oz - camera.z, r, rectScratch);
    }

    /** Screen rect of a model's bounds as placed (no bounds were given). */
    private ScreenRect modelRect(ModelDraw md, FxModels.Mesh mesh) {
        float[] a = md.axes();
        float lx = (mesh.min()[0] + mesh.max()[0]) * 0.5f;
        float ly = (mesh.min()[1] + mesh.max()[1]) * 0.5f;
        float lz = (mesh.min()[2] + mesh.max()[2]) * 0.5f;
        double cx = md.ox() + a[0] * lx + a[3] * ly + a[6] * lz;
        double cy = md.oy() + a[1] * lx + a[4] * ly + a[7] * lz;
        double cz = md.oz() + a[2] * lx + a[5] * ly + a[8] * lz;
        double dx = mesh.max()[0] - mesh.min()[0];
        double dy = mesh.max()[1] - mesh.min()[1];
        double dz = mesh.max()[2] - mesh.min()[2];
        float local = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.5f;
        if (md.pose() != null) {
            // posed bones may reach past the rest bounds: grow the sphere by how far they carry it
            mesh.rig().solve(md.pose());
            local = mesh.rig().reach(lx, ly, lz, local);
        }
        double radius = local * md.scale() + 0.05;
        return ScreenRect.ofSphere(viewProj, cx, cy, cz, radius, rectScratch);
    }

    /** Draw one model into the bound target (depth, cull and blend state already set). */
    /** Bone matrices on their way to {@code MBones} (3 vec4 a bone). */
    private final java.nio.FloatBuffer boneBuffer = org.lwjgl.BufferUtils.createFloatBuffer(FxPose.MAX_BONES * 12);

    /** Whether this model entry's material samples the image behind it ({@code DiffuseSampler}). */
    private boolean readsImage(PassListImpl.Entry e) {
        ShaderProgram program = shaders.getModel(e.shader());
        return program != null && program.location("DiffuseSampler") >= 0;
    }

    private void drawModel(PassListImpl.Entry e, int input, int depth, float time, long now) {
        ModelDraw md = e.model();
        FxModels.Mesh mesh = models.get(md.model().file(), now);
        ShaderProgram program = shaders.getModel(e.shader());
        if (mesh == null || program == null) {
            return;
        }
        begin(program, input, depth, fxW, fxH, time);
        FxTexture texture = md.model().texture();
        if (texture != null) {
            binder.texture("MTexture", texture);
        } else {
            binder.blankTexture("MTexture");
        }
        binder.f4("MColor", 1f, 1f, 1f, 1f);
        binder.f4("MParams", 0f, 0f, 0f, 0f);
        binder.f4("MMode", 0f, md.light(), e.presence(), 0f);
        binder.f3("MOrigin", md.ox(), md.oy(), md.oz());
        ParticleBatch pieces = md.pieces();
        boolean instanced = md.instances() != null || pieces != null;
        binder.f1("MInstanced", pieces != null ? 2f : instanced ? 1f : 0f);
        if (pieces != null) {
            particles.bind(pieces, program);
            particleMotion(program, pieces, e.time());
            program.set4(ShaderProgram.U_PShape, pieces.facing.ordinal(), pieces.stretch, pieces.pivot, 0f);
            program.set4(ShaderProgram.U_PCurves, pieces.ramp != null ? 0f : pieces.colorKeyCount, pieces.alphaKeyCount,
                    pieces.sizeKeyCount, 0f);
            if (pieces.colorKeyCount > 0) {
                float[] k = pieces.colorKeys;
                program.set3(ShaderProgram.U_PColorKey0, k[0], k[1], k[2]);
                program.set3(ShaderProgram.U_PColorKey1, k[3], k[4], k[5]);
                program.set3(ShaderProgram.U_PColorKey2, k[6], k[7], k[8]);
                program.set3(ShaderProgram.U_PColorKey3, k[9], k[10], k[11]);
            }
            program.set4(ShaderProgram.U_PAlphaKeys, pieces.alphaKeys[0], pieces.alphaKeys[1], pieces.alphaKeys[2], pieces.alphaKeys[3]);
            program.set4(ShaderProgram.U_PSizeKeys, pieces.sizeKeys[0], pieces.sizeKeys[1], pieces.sizeKeys[2], pieces.sizeKeys[3]);
        }
        // posed bones: one 3x4 matrix each, applied in the vertex shader (no re-bake, no extra draw)
        int bones = 0;
        if (md.pose() != null) {
            BoneRig rig = mesh.rig();
            float[] skin = rig.solve(md.pose());
            int loc = program.location("MBones");
            if (rig.moved() > 0 && loc >= 0) {
                bones = rig.count();
                boneBuffer.clear();
                boneBuffer.put(skin, 0, bones * 12).flip();
                GL20.glUniform4fv(loc, boneBuffer);
            }
        }
        binder.f1("MBoneCount", bones);
        if (md.instances() != null) {
            binder.data("MInstances", md.instances());
        }
        int axes = program.location("MAxes");
        if (axes >= 0) {
            GL20.glUniformMatrix3fv(axes, false, md.axes());
        }
        try {
            e.setup().accept(binder);
        } catch (RuntimeException ex) {
            if (failedModelSetups.add(e.shader())) {
                BHFxMod.LOGGER.error("bhfx uniform setup for model material {} threw", e.shader(), ex);
            }
            if (pieces != null) {
                particles.unbind();
            }
            return;
        }
        if (instanced) {
            // copies may be mirrored or not: no culling here, the footer drops each copy's back faces itself
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL30.glBindVertexArray(mesh.vao());
            int copies = pieces != null ? Math.min(instances(pieces), pieces.live) : md.count();
            if (copies > 0) {
                org.lwjgl.opengl.GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, mesh.vertexCount(), copies);
            }
            GL11.glEnable(GL11.GL_CULL_FACE);
            if (pieces != null) {
                particles.unbind();
            }
            return;
        }
        // a mirrored frame (e.g. a BodyPose's right/up/forward) turns every triangle over: cull the other side
        float[] a = md.axes();
        float det = a[0] * (a[4] * a[8] - a[7] * a[5]) - a[3] * (a[1] * a[8] - a[7] * a[2]) + a[6] * (a[1] * a[5] - a[4] * a[2]);
        if (det < 0f) {
            GL11.glFrontFace(GL11.GL_CW);
        }
        GL30.glBindVertexArray(mesh.vao());
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, mesh.vertexCount());
        if (det < 0f) {
            GL11.glFrontFace(GL11.GL_CCW);
        }
    }

    /** Framebuffers over each ping texture with one shared depth buffer, rebuilt when the targets change. */
    private void ensureModelTargets() {
        if (modelDepth != 0 && modelW == fxW && modelH == fxH && modelColor[0] == pingTex[0]
                && modelColor[1] == pingTex[1]) {
            return;
        }
        releaseModelTargets();
        modelDepth = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, modelDepth);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, fxW, fxH);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        for (int i = 0; i < 2; i++) {
            modelFbo[i] = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, modelFbo[i]);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, pingTex[i], 0);
            GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, modelDepth);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("incomplete model framebuffer (0x" + Integer.toHexString(status) + ")");
            }
            modelColor[i] = pingTex[i];
        }
        modelW = fxW;
        modelH = fxH;
    }

    private void releaseModelTargets() {
        for (int i = 0; i < 2; i++) {
            if (modelFbo[i] != 0) {
                GL30.glDeleteFramebuffers(modelFbo[i]);
                modelFbo[i] = 0;
            }
            modelColor[i] = 0;
        }
        if (modelDepth != 0) {
            GL30.glDeleteRenderbuffers(modelDepth);
            modelDepth = 0;
        }
        modelW = 0;
        modelH = 0;
    }

    /** The uniforms both vertex shaders need to move a batch's particles: origin, clock, motion, orbit, ring, window. */
    private void particleMotion(ShaderProgram program, ParticleBatch b, float batchTime) {
        program.set3(ShaderProgram.U_POrigin, (float) (b.ox - camera.x), (float) (b.oy - camera.y), (float) (b.oz - camera.z));
        program.set1(ShaderProgram.U_PTime, batchTime);
        program.set4(ShaderProgram.U_PMotion, b.gx, b.gy, b.gz, b.drag);
        program.set3(ShaderProgram.U_POrbit, b.orbitX(), b.orbitY(), b.orbitZ());
        program.set3(ShaderProgram.U_POrbitAxis, b.oax, b.oay, b.oaz);
        // w: size gain of the slots a batch's own LOD leaves (so the cloud keeps its weight); the frame's budget
        // thinning does not grow them
        program.set4(ShaderProgram.U_PRing, b.capacityValue(), b.filled, strideOf(b),
                b.lodStride > 1 && b.facing != FxParticles.Facing.RIBBON ? (float) Math.sqrt(b.lodStride) : 1f);
        ParticleBatch.windowRuns(b.liveStart(), b.live, b.capacityValue(), strideOf(b), runs);
        program.set4(ShaderProgram.U_PWindow, runs[0], runs[1], 0f, 0f);
    }

    /** Draw one particle batch into the bound target (blend state already set). */
    private void drawBatch(PassListImpl.Entry e, int input, int depth, float time) {
        ParticleBatch b = e.particles();
        ShaderProgram program = shaders.getParticle(e.shader());
        if (program == null) {
            return;
        }
        long p0 = System.nanoTime();
        begin(program, input, depth, fxW, fxH, time);
        long p1 = System.nanoTime();
        particles.bind(b, program);
        long p2 = System.nanoTime();
        phases[PH_UPLOAD] += p2 - p1;
        particleMotion(program, b, e.time());
        program.set4(ShaderProgram.U_PShape, b.facing.ordinal(), b.stretch, b.pivot, 0f);
        program.set4(ShaderProgram.U_PMode, b.blend.ordinal(), b.softness, b.distortion, e.presence());
        program.set3(ShaderProgram.U_PRibbonNormal, b.rnx, b.rny, b.rnz);
        program.set4(ShaderProgram.U_PNear, b.nearGone, b.nearFull, b.maxScreen, 0f);
        program.set4(ShaderProgram.U_PTurb, b.turbAmp, b.turbFreq, b.turbScale, 0f);
        float colourMode = b.colorKeyCount;
        if (b.ramp != null) {
            // binding is what uploads a decoded texture (FxTextures.get); until then the 1x1 fallback is bound unused
            binder.texture("PRamp", b.ramp);
            if (textureReady(b.ramp)) {
                program.set2(ShaderProgram.U_PRampRange, b.rampFrom, b.rampTo);
                colourMode = -1f;
            }
        }
        program.set4(ShaderProgram.U_PCurves, colourMode, b.alphaKeyCount, b.sizeKeyCount, 0f);
        if (b.colorKeyCount > 0) {
            float[] k = b.colorKeys;
            program.set3(ShaderProgram.U_PColorKey0, k[0], k[1], k[2]);
            program.set3(ShaderProgram.U_PColorKey1, k[3], k[4], k[5]);
            program.set3(ShaderProgram.U_PColorKey2, k[6], k[7], k[8]);
            program.set3(ShaderProgram.U_PColorKey3, k[9], k[10], k[11]);
        }
        program.set4(ShaderProgram.U_PAlphaKeys, b.alphaKeys[0], b.alphaKeys[1], b.alphaKeys[2], b.alphaKeys[3]);
        program.set4(ShaderProgram.U_PSizeKeys, b.sizeKeys[0], b.sizeKeys[1], b.sizeKeys[2], b.sizeKeys[3]);
        try {
            e.setup().accept(binder);
        } catch (RuntimeException ex) {
            if (failedSetups.add(e.shader())) {
                BHFxMod.LOGGER.error("bhfx uniform setup for particle material {} threw", e.shader(), ex);
            }
            particles.unbind();
            return;
        }
        long p3 = System.nanoTime();
        phases[PH_UNIFORM] += (p1 - p0) + (p3 - p2);
        particles.draw(b, instances(b));
        particles.unbind();
        phases[PH_PDRAW] += System.nanoTime() - p3;
    }

    /**
     * Pick this frame's particle stride: when the visible non-ribbon batches want more slots than the cap leaves after
     * ribbons, every batch draws one slot in {@code stride} (an even thinning of all effects, the same slots each frame)
     * instead of whole batches being skipped. Goes up at once, comes down only after it settled.
     */
    private void updateParticleStride(List<PassListImpl.Entry> passes, int budget) {
        long wanted = 0;
        int ribbons = 0;
        for (PassListImpl.Entry e : passes) {
            ParticleBatch b = e.particles();
            if (b == null || b.closed()) {
                continue;
            }
            b.updateStats();
            b.advanceWindow(e.time()); // dead slots at the old end are no longer drawn or counted
            ScreenRect r = e.radius() > 0
                    ? ScreenRect.ofSphere(viewProj, e.bx(), e.by(), e.bz(), e.radius(), rectScratch)
                    : ScreenRect.ofSphere(viewProj, b.ox - camera.x, b.oy - camera.y, b.oz - camera.z, b.cullRadius(), rectScratch);
            if (r.empty()) {
                continue;
            }
            if (b.facing == FxParticles.Facing.RIBBON) {
                ribbons += b.live;
            } else {
                if (b.lodDistance > 0f) {
                    double dx = b.ox - camera.x, dy = b.oy - camera.y, dz = b.oz - camera.z;
                    b.lodStride = ParticleBatch.lodStride(b.lodDistance,
                            Math.sqrt(dx * dx + dy * dy + dz * dz), b.lodStride);
                }
                wanted += b.live / b.lodStride;
            }
        }
        long room = Math.max(1, budget - Math.min(ribbons, budget));
        int need = (int) Math.max(1, Math.min(64, (wanted + room - 1) / room));
        if (need >= particleStride) {
            particleStride = need;
            strideLowFrames = 0;
        } else if (++strideLowFrames >= STRIDE_SETTLE_FRAMES) {
            particleStride = need;
            strideLowFrames = 0;
        }
    }

    private int strideOf(ParticleBatch b) {
        return b.facing == FxParticles.Facing.RIBBON ? 1 : Math.max(particleStride, b.lodStride);
    }

    /** Live-window runs of the batch being drawn (see {@link ParticleBatch#windowRuns}). */
    private final int[] runs = new int[3];

    /** Instances drawn for a batch: its live window, every slot for ribbons, one in {@link #particleStride} for the rest. */
    private int instances(ParticleBatch b) {
        ParticleBatch.windowRuns(b.liveStart(), b.live, b.capacityValue(), strideOf(b), runs);
        return runs[1] + runs[2];
    }

    /** Particle slots drawn last frame. */
    public int lastParticleCount() {
        return lastParticleCount;
    }

    /** Current particle stride, for /bhfx status. */
    public int particleStride() {
        return particleStride;
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

    private void ensureLayer(float share) {
        int lw = Math.max(2, Math.round(fxW * share));
        int lh = Math.max(2, Math.round(fxH * share));
        if (layerW == lw && layerH == lh && layerFbo != 0) {
            return;
        }
        guard.touchUnit(0);
        if (layerFbo == 0) {
            layerFbo = GL30.glGenFramebuffers();
            layerTex = GL11.glGenTextures();
        }
        texture(layerTex, lw, lh, GL30.GL_RGBA16F, GL11.GL_FLOAT);
        attach(layerFbo, layerTex, "layer");
        layerW = lw;
        layerH = lh;
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
        bindUnit(UNIT_INPUT, input, program, ShaderProgram.U_DiffuseSampler);
        bindUnit(UNIT_SCENE, snapshotTex, program, ShaderProgram.U_SceneSampler);
        bindUnit(UNIT_DEPTH, depth, program, ShaderProgram.U_DepthSampler);
        // unchanged values are not sent again (ShaderProgram keeps what each program last got)
        program.setMatrix(ShaderProgram.U_InvProj, invProj, matrixBuf);
        program.setMatrix(ShaderProgram.U_InvView, invView, matrixBuf);
        program.setMatrix(ShaderProgram.U_ViewProj, viewProj, matrixBuf);
        program.set2(ShaderProgram.U_OutSize, outW, outH);
        program.set2(ShaderProgram.U_ScreenSize, fullW, fullH);
        program.set1(ShaderProgram.U_GameTime, time);
        program.set3(ShaderProgram.U_Eye, eye.x, eye.y, eye.z);
        program.set4(ShaderProgram.U_FogColor, fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
        program.set4(ShaderProgram.U_FogRange, fogRange[0], fogRange[1], fogRange[2], 0f);
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

    private void bindUnit(int unit, int texture, ShaderProgram program, int sampler) {
        guard.touchUnit(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        program.setInt(sampler, unit);
    }

    private void bindUnit(int unit, int texture, ShaderProgram program, String sampler) {
        guard.touchUnit(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        program.setInt(sampler, unit);
    }

    /** rgb as 0..1 (display space) plus a fourth value. */
    private void rgb(ShaderProgram p, String name, int rgb, float w) {
        binder.f4(name, ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, w);
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
        deleteFbo(layerFbo);
        deleteTex(layerTex);
        layerFbo = layerTex = layerW = layerH = 0;
        particles.releaseAll();
        tables.releaseAll();
        releaseModelTargets();
        models.clear();
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
