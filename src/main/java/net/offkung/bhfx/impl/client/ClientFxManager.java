package net.offkung.bhfx.impl.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhfx.BHFxMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.offkung.bhfx.api.FxParams;
import net.offkung.bhfx.api.FxSpawn;
import net.offkung.bhfx.api.client.FxSettings;
import net.offkung.bhfx.api.StopMode;
import net.offkung.bhfx.api.client.Fx;
import net.offkung.bhfx.api.client.FxFactory;
import net.offkung.bhfx.api.client.FxInit;
import net.offkung.bhfx.api.client.LocalFxHandle;
import net.offkung.bhfx.impl.BHFxConfig;
import net.offkung.bhfx.impl.render.PostRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns every running effect on this client. Render thread only. */
public final class ClientFxManager {
    private static final long RELEASE_AFTER_NANOS = 30_000_000_000L;

    private static final class Entry {
        final Fx fx;
        final int handleId;
        boolean followSeen;
        boolean broken;

        Entry(Fx fx, int handleId) {
            this.fx = fx;
            this.handleId = handleId;
        }
    }

    private static final Map<ResourceLocation, FxFactory> FACTORIES = new HashMap<>();
    private static final Set<ResourceLocation> WARNED = new HashSet<>();
    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final FxContextImpl TICK = new FxContextImpl();
    private static final FrameContextImpl FRAME = new FrameContextImpl();
    private static final PassListImpl PASSES = new PassListImpl();
    private static final ScreenParamsImpl SCREEN = new ScreenParamsImpl(FRAME);
    private static final long DOWNGRADE_AFTER_NANOS = 5_000_000_000L;
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static final Matrix4f VIEW_PROJ = new Matrix4f();

    @Nullable
    private static PostRenderer renderer;
    private static int nextLocalId = -1;
    private static boolean runtimeOff;
    private static float trauma;
    private static float frameShake;
    private static long lastPostNanos;
    private static int lastPassCount;
    /** Session-only quality ceiling set by the automatic downgrade; HIGH = no ceiling. */
    private static BHFxConfig.Quality qualityCeiling = BHFxConfig.Quality.HIGH;
    private static long overBudgetSince;
    @Nullable
    private static ClientLevel lastLevel;

    private static final FxSettings SETTINGS = new FxSettings() {
        @Override
        public Quality quality() {
            return Quality.valueOf(effectiveQuality().name());
        }

        @Override
        public boolean bloom() {
            return effectiveQuality().bloom && BHFxConfig.BLOOM.get();
        }

        @Override
        public boolean impactFrames() {
            return BHFxConfig.IMPACT_FRAMES.get();
        }

        @Override
        public double flashScale() {
            return BHFxConfig.FLASH_SCALE.get();
        }

        @Override
        public double shakeScale() {
            return BHFxConfig.SHAKE_SCALE.get();
        }

        @Override
        public double gpuBudgetMs() {
            return BHFxConfig.GPU_BUDGET_MS.get();
        }
    };

    private ClientFxManager() {
    }

    // ------------------------------------------------------------------ registration / state

    public static void register(ResourceLocation id, FxFactory factory) {
        if (FACTORIES.put(id, factory) != null) {
            BHFxMod.LOGGER.warn("bhfx effect {} registered twice; the last registration wins", id);
        }
    }

    public static boolean enabled() {
        return !runtimeOff && BHFxConfig.QUALITY.get() != BHFxConfig.Quality.OFF;
    }

    /** Configured quality, capped by an automatic downgrade this session. */
    static BHFxConfig.Quality effectiveQuality() {
        return BHFxConfig.QUALITY.get().min(qualityCeiling);
    }

    public static FxSettings settings() {
        return SETTINGS;
    }

    public static boolean postAvailable() {
        return enabled() && (renderer == null || !renderer.disabled());
    }

    static void setRuntimeOff(boolean off) {
        runtimeOff = off;
        if (off) {
            clear();
        }
    }

    static boolean runtimeOff() {
        return runtimeOff;
    }

    private static PostRenderer renderer() {
        if (renderer == null) {
            renderer = new PostRenderer();
        }
        return renderer;
    }

    public static void reloadShaders() {
        qualityCeiling = BHFxConfig.Quality.HIGH;
        overBudgetSince = 0L;
        if (renderer != null) {
            renderer.reload();
            renderer.resetBudget();
        }
    }

    // ------------------------------------------------------------------ start / stop

    static void start(FxInit init) {
        if (enabled()) {
            add(init);
        }
    }

    public static LocalFxHandle startLocal(ResourceLocation fxId, FxSpawn spawn) {
        if (!enabled() || Minecraft.getInstance().level == null) {
            return LocalFxHandle.NONE;
        }
        int id = nextLocalId--;
        if (nextLocalId >= 0) {
            nextLocalId = -1;
        }
        long seed = spawn.seed() != null ? spawn.seed() : RandomSource.create().nextLong();
        FxInit init = new FxInit(fxId, id, spawn.origin(), spawn.target(), spawn.casterId(), spawn.followId(),
                spawn.follow(), seed, spawn.duration(), 0, spawn.builtParams());
        return add(init) ? new LocalFxHandle(id) : LocalFxHandle.NONE;
    }

    private static boolean add(FxInit init) {
        if (init.handleId() > 0) {
            for (Entry e : ACTIVE) {
                if (e.handleId == init.handleId()) {
                    return false; // already playing (the server re-sent it after we came back into range)
                }
            }
        }
        FxFactory factory = FACTORIES.get(init.id());
        if (factory == null) {
            if (WARNED.add(init.id())) {
                BHFxMod.LOGGER.warn("bhfx effect {} is not registered on this client; ignoring it", init.id());
            }
            return false;
        }
        Fx fx;
        try {
            fx = factory.create(init);
        } catch (RuntimeException e) {
            BHFxMod.LOGGER.error("bhfx effect {} failed to construct", init.id(), e);
            return false;
        }
        int max = BHFxConfig.MAX_ACTIVE_FX.get();
        while (ACTIVE.size() >= max) {
            remove(ACTIVE.remove(0));
        }
        ACTIVE.add(new Entry(fx, init.handleId()));
        return true;
    }

    static void stop(int handleId, StopMode mode) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Entry e : ACTIVE) {
            if (e.handleId == handleId) {
                e.fx.bhfxStop(TICK.bind(level, e.fx, followed(level, e.fx)), mode);
                return;
            }
        }
    }

    static void update(int handleId, FxParams changed) {
        for (Entry e : ACTIVE) {
            if (e.handleId == handleId) {
                try {
                    e.fx.bhfxParams(changed);
                } catch (RuntimeException ex) {
                    BHFxMod.LOGGER.error("bhfx effect {} failed in onParamsChanged", e.fx.init().id(), ex);
                    e.broken = true;
                }
                return;
            }
        }
    }

    public static void stopLocal(LocalFxHandle handle, StopMode mode) {
        if (handle != null && !handle.isNone()) {
            stop(handle.id(), mode);
        }
    }

    public static void shake(Vec3 source, float amount, double range) {
        if (!enabled()) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float falloff = (float) Math.max(0.0, 1.0 - camera.distanceTo(source) / Math.max(range, 1.0));
        trauma = Math.min(1f, trauma + amount * falloff);
    }

    static void clear() {
        for (Entry e : ACTIVE) {
            remove(e);
        }
        ACTIVE.clear();
        trauma = 0f;
        frameShake = 0f;
    }

    private static void remove(Entry e) {
        try {
            e.fx.bhfxRemoved();
        } catch (RuntimeException ex) {
            BHFxMod.LOGGER.error("bhfx effect {} failed in onRemoved", e.fx.init().id(), ex);
        }
    }

    @Nullable
    private static Entity followed(ClientLevel level, Fx fx) {
        int id = fx.init().followId();
        if (id < 0) {
            return null;
        }
        Entity entity = level.getEntity(id);
        return entity == null || entity.isRemoved() ? null : entity;
    }

    // ------------------------------------------------------------------ tick

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            // respawn / dimension change / disconnect: the server re-sends what is still playing
            clear();
            lastLevel = level;
        }
        if (level == null) {
            releaseIfIdle();
            return;
        }
        if (!enabled() && !ACTIVE.isEmpty()) {
            clear();
        }
        trauma = Math.max(0f, trauma - 0.04f);

        Iterator<Entry> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            Entity follow = followed(level, e.fx);
            if (e.fx.init().followId() >= 0) {
                if (follow != null) {
                    e.followSeen = true;
                } else if (e.followSeen) {
                    // the entity this effect was attached to is gone
                    e.fx.bhfxStop(TICK.bind(level, e.fx, null), StopMode.CANCEL);
                }
            }
            try {
                e.fx.bhfxTick(TICK.bind(level, e.fx, follow));
            } catch (RuntimeException ex) {
                BHFxMod.LOGGER.error("bhfx effect {} failed while ticking; removing it", e.fx.init().id(), ex);
                e.broken = true;
            }
            if (e.broken || e.fx.bhfxFinished()) {
                it.remove();
                remove(e);
            }
        }
        releaseIfIdle();
    }

    /** Give the effect targets back to the GPU after 30 s without post-processing. */
    private static void releaseIfIdle() {
        if (renderer != null && renderer.allocated() && System.nanoTime() - lastPostNanos > RELEASE_AFTER_NANOS) {
            renderer.release();
        }
    }

    // ------------------------------------------------------------------ render

    static void captureMatrices(Matrix4f view, Matrix4f projection) {
        VIEW.set(view);
        PROJ.set(projection);
    }

    static void render(Camera camera, float partial) {
        frameShake = 0f;
        lastPassCount = 0;
        if (!enabled() || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        VIEW_PROJ.set(PROJ).mul(VIEW);
        Vec3 cam = camera.getPosition();
        FRAME.begin(partial, cam, mc.getMainRenderTarget().width, mc.getMainRenderTarget().height, VIEW_PROJ, PROJ.m11());
        PASSES.clear();
        SCREEN.reset();
        double cullCap = BHFxConfig.CULL_DISTANCE.get();
        for (Entry e : ACTIVE) {
            if (e.broken) {
                continue;
            }
            FRAME.bind(e.fx, followed(level, e.fx));
            double distance = e.fx.bhfxFocus(FRAME).distanceTo(cam);
            if (distance > Math.min(e.fx.bhfxCullDistance(), cullCap)) {
                continue;
            }
            PASSES.currentDistance(distance);
            try {
                e.fx.bhfxFrame(FRAME, PASSES, SCREEN);
            } catch (RuntimeException ex) {
                BHFxMod.LOGGER.error("bhfx effect {} failed while rendering; removing it", e.fx.init().id(), ex);
                e.broken = true;
            }
        }
        frameShake = SCREEN.shakeValue();
        if (PASSES.isEmpty() && SCREEN.imageIdle()) {
            return;
        }
        PostRenderer r = renderer();
        if (r.disabled()) {
            return;
        }
        List<PassListImpl.Entry> draw = PASSES.drawList(BHFxConfig.MAX_PASSES_PER_FRAME.get());
        float time = ((level.getGameTime() % 24000L) + partial) / 20f;
        r.render(draw, SCREEN, PROJ, VIEW, time, effectiveQuality());
        lastPassCount = r.lastPassCount();
        lastPostNanos = System.nanoTime();
        checkBudget(r, lastPostNanos);
    }

    /** Lower the quality for this session when effects stay over budget even at the lowest resolution. */
    private static void checkBudget(PostRenderer r, long now) {
        double budget = BHFxConfig.GPU_BUDGET_MS.get();
        if (!BHFxConfig.AUTO_DOWNGRADE.get() || !r.overBudget(budget)) {
            overBudgetSince = 0L;
            return;
        }
        if (overBudgetSince == 0L) {
            overBudgetSince = now;
            return;
        }
        BHFxConfig.Quality current = effectiveQuality();
        if (now - overBudgetSince < DOWNGRADE_AFTER_NANOS || current == BHFxConfig.Quality.LOW) {
            return;
        }
        qualityCeiling = current.lower();
        overBudgetSince = 0L;
        r.resetBudget();
        String msg = String.format("[bhfx] effects used %.1f ms of GPU per frame (budget %.1f ms); quality lowered to %s for this session.",
                r.averageGpuMs(), budget, qualityCeiling);
        BHFxMod.LOGGER.warn(msg);
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal(msg).withStyle(ChatFormatting.GOLD));
    }

    /** Lines for the F3 screen. */
    static void debugLines(List<String> out) {
        if (!enabled()) {
            out.add("[bhfx] off");
            return;
        }
        out.add("[bhfx] " + ACTIVE.size() + " fx, " + lastPassCount + " pass, " + effectiveQuality()
                + (renderer != null ? ", " + renderer.describe() : ""));
    }

    static void renderHud(GuiGraphics graphics, float partial) {
        if (!enabled() || ACTIVE.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Entry e : ACTIVE) {
            if (!e.broken) {
                e.fx.bhfxHud(graphics, FRAME.bind(e.fx, followed(level, e.fx)));
            }
        }
    }

    /** Camera shake amount for this frame, before the comfort multiplier. */
    static float shakeAmount() {
        return trauma * trauma + frameShake * 0.6f;
    }

    static String status() {
        StringBuilder sb = new StringBuilder();
        sb.append("bhfx: ").append(enabled() ? "on" : "off");
        if (runtimeOff) {
            sb.append(" (/bhfx off)");
        }
        sb.append(", quality ").append(effectiveQuality());
        if (effectiveQuality() != BHFxConfig.QUALITY.get()) {
            sb.append(" (auto-lowered from ").append(BHFxConfig.QUALITY.get()).append(", /bhfx reload resets)");
        }
        sb.append(", ").append(ACTIVE.size()).append(" fx, ").append(lastPassCount).append(" pass(es) last frame");
        if (renderer != null) {
            sb.append(", ").append(renderer.describe());
        }
        sb.append(", ").append(FACTORIES.size()).append(" registered");
        return sb.toString();
    }
}
