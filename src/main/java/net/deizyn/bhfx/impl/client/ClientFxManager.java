package net.deizyn.bhfx.impl.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.FxSpawn;
import net.deizyn.bhfx.api.ScreenPulse;
import net.deizyn.bhfx.api.client.FxSettings;
import net.deizyn.bhfx.api.StopMode;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxFactory;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.LocalFxHandle;
import net.deizyn.bhfx.impl.BHFxConfig;
import net.deizyn.bhfx.impl.render.ParticleBatch;
import net.deizyn.bhfx.impl.render.PostRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Owns every running effect on this client. Render thread only. */
public final class ClientFxManager {
    private static final long RELEASE_AFTER_NANOS = 30_000_000_000L;
    private static final int MAX_PULSES = 16;

    /** Ticks a late-built effect takes to fade in, and a hidden one to fade out. */
    private static final int FADE_TICKS = 10;
    /** A shown effect stays at least this long (2 s), and keeps its place until this far past an edge. */
    private static final int HOLD_TICKS = 40;
    private static final double MARGIN = 8.0;
    /** Effects this close to the player are built at once, outside the per-tick construction budget. */
    private static final double BUILD_NOW_DISTANCE = 16.0;
    /** Constructor time per tick for everything else; the rest waits for the next tick. */
    private static final long BUILD_BUDGET_NANOS = 2_000_000L;
    /** Built within this many ticks of arriving = on time (keeps onStart); later = caught up to its age. */
    private static final int LATE_TICKS = 2;
    /** Most effects known at once (built or not); past it the farthest unbuilt one is forgotten. */
    private static final int MAX_KNOWN = 1024;

    /**
     * One effect this client knows about (scale plan phase 1). It has an {@link Fx} only while {@link FxSelection}
     * shows it; when shown again later it is built anew at its current age from the stored start data and params.
     */
    private static final class Entry {
        final FxInit init;
        final int handleId;
        /** Client tick it arrived; its age now is {@code init.startAge + clientTicks - receivedAt}. */
        final long receivedAt;
        FxParams params;
        @Nullable
        Fx fx;
        boolean followSeen;
        boolean broken;
        boolean stopping;
        long shownSince;
        /** 0..1, drawn as Presence; {@link #fadeStep} per tick (+ appearing, - being hidden, 0 steady). */
        float presence = 1f;
        float fadeStep;
        /** Lifetime in ticks if known (UNTIL_STOPPED = only a stop ends it). */
        int duration;
        double distance;
        boolean pinned;
        /** A local effect nobody holds (/bhfx stress): it may be hidden like a server one. */
        boolean mayHide;

        Entry(FxInit init, long receivedAt) {
            this.init = init;
            this.handleId = init.handleId();
            this.receivedAt = receivedAt;
            this.params = init.params();
            this.duration = init.duration() == FxSpawn.DEFAULT_DURATION
                    ? DEFAULT_TICKS.getOrDefault(init.id(), 200) : init.duration();
        }

        int ageNow() {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0, init.startAge()) + (clientTicks - receivedAt));
        }

        boolean local() {
            return handleId < 0;
        }

        float presenceAt(float partial) {
            return Math.max(0f, Math.min(1f, presence + fadeStep * partial));
        }
    }

    /** Default lifetimes learned from built effects, for effects not built yet that rely on them. */
    private static final Map<ResourceLocation, Integer> DEFAULT_TICKS = new HashMap<>();
    private static final List<FxSelection.Candidate> CANDIDATES = new ArrayList<>();
    private static long clientTicks;

    private static final Map<ResourceLocation, FxFactory> FACTORIES = new HashMap<>();
    private static final Set<ResourceLocation> WARNED = new HashSet<>();
    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final List<ActivePulse> PULSES = new ArrayList<>();
    private static final FxContextImpl TICK = new FxContextImpl();
    private static final FrameContextImpl FRAME = new FrameContextImpl();
    private static final PassListImpl PASSES = new PassListImpl();
    private static final ScreenParamsImpl SCREEN = new ScreenParamsImpl(FRAME);
    private static final long DOWNGRADE_AFTER_NANOS = 5_000_000_000L;
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final ShaderWarmup WARMUP = new ShaderWarmup();
    private static final FrameStats STATS = new FrameStats();
    private static final FxBench BENCH = new FxBench();
    private static final CameraParamsImpl CAMERA = new CameraParamsImpl();

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

    // counters for /bhfx stats and /bhfx stress (reset on world change and by /bhfx stress)
    private static int peakActive;
    private static int evicted;
    private static int builtLate;
    private static int forgotten;
    private static int failed;
    private static int unregistered;
    private static int startsThisTick;
    private static long constructNanosThisTick;
    private static int busiestTickStarts;
    private static long busiestTickConstructNanos;
    private static long longestConstructNanos;
    @Nullable
    private static ResourceLocation longestConstructId;
    private static int sampleClock;

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
        return enabled() && (renderer == null || !renderer.disabled()) && !ShaderpackDetector.blocksPost();
    }

    public static void preloadTexture(net.deizyn.bhfx.api.client.FxTexture texture) {
        if (enabled()) {
            renderer().preload(texture);
        }
    }

    public static boolean textureReady(net.deizyn.bhfx.api.client.FxTexture texture) {
        return renderer != null && renderer.textureReady(texture);
    }

    public static boolean drawingMirrorReflection() {
        return MirrorRenderer.drawing();
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
        if (Minecraft.getInstance().level != null) {
            scheduleWarmup();
        }
    }

    /** The settings screen changed a value: drop an automatic downgrade and resolution history so it applies now. */
    static void settingsChanged() {
        qualityCeiling = BHFxConfig.Quality.HIGH;
        overBudgetSince = 0L;
        if (renderer != null) {
            renderer.resetBudget();
        }
    }

    /** One short status line for the settings screen. */
    static String liveLine() {
        if (!enabled()) {
            return "Effects off";
        }
        StringBuilder sb = new StringBuilder("Now: ").append(builtCount()).append(" fx, quality ").append(effectiveQuality());
        if (renderer != null && renderer.allocated()) {
            double ms = renderer.averageGpuMs();
            sb.append(", effects at ").append(renderer.effectSize())
                    .append(" (").append(Math.round(renderer.resolutionShare() * 100)).append("%)");
            if (ms >= 0 && !ACTIVE.isEmpty()) {
                sb.append(String.format(", %.2f ms GPU", ms));
            }
        }
        if (ShaderpackDetector.blocksPost()) {
            sb.append(", post off (shader pack)");
        }
        return sb.toString();
    }

    private static void scheduleWarmup() {
        if (BHFxConfig.WARMUP.get()) {
            WARMUP.schedule();
        } else {
            WARMUP.cancel();
        }
    }

    // ------------------------------------------------------------------ start / stop

    static void start(FxInit init) {
        if (enabled()) {
            add(init, false);
        }
    }

    public static LocalFxHandle startLocal(ResourceLocation fxId, FxSpawn spawn) {
        return startLocal(fxId, spawn, false);
    }

    /**
     * {@code mayHide}: nobody holds this effect's {@link Fx} (e.g. /bhfx stress), so the selection may hide and rebuild
     * it like a server effect. Local effects started through the API are always built (callers may keep their Fx).
     */
    static LocalFxHandle startLocal(ResourceLocation fxId, FxSpawn spawn, boolean mayHide) {
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
        Entry e = add(init, mayHide);
        return e != null && (e.fx != null || mayHide) ? new LocalFxHandle(id) : LocalFxHandle.NONE;
    }

    /**
     * Remember a new effect and build it now when it is pinned, close, or fits this tick's construction budget and the
     * cap; otherwise the next selection decides. Local effects are always built at once and never hidden, because
     * callers may hold their {@link Fx} (BHFxClient.fx).
     */
    @Nullable
    private static Entry add(FxInit init, boolean mayHide) {
        if (init.handleId() > 0) {
            for (Entry e : ACTIVE) {
                if (e.handleId == init.handleId()) {
                    return null; // already known (the server re-sent it after we came back into range)
                }
            }
        }
        if (!FACTORIES.containsKey(init.id())) {
            unregistered++;
            if (WARNED.add(init.id())) {
                BHFxMod.LOGGER.warn("bhfx effect {} is not registered on this client; ignoring it", init.id());
            }
            return null;
        }
        Entry e = new Entry(init, clientTicks);
        e.mayHide = mayHide;
        measure(Minecraft.getInstance(), e);
        ACTIVE.add(e);
        boolean inner = e.distance <= BHFxConfig.GUARANTEED_RADIUS.get();
        if (e.pinned || e.distance <= BUILD_NOW_DISTANCE || (constructNanosThisTick < BUILD_BUDGET_NANOS
                && (inner || builtCount() < BHFxConfig.MAX_ACTIVE_FX.get()))) {
            build(e);
        }
        if (e.broken) {
            ACTIVE.remove(e);
            return null;
        }
        forgetIfTooMany();
        return e;
    }

    /** Construct the effect of {@code e} at its age now (or as sent, when on time). Sets broken when it throws. */
    private static void build(Entry e) {
        FxFactory factory = FACTORIES.get(e.init.id());
        if (factory == null) {
            e.broken = true;
            return;
        }
        int late = (int) (clientTicks - e.receivedAt);
        boolean onTime = late <= LATE_TICKS;
        FxInit init = e.init;
        if (!onTime || e.params != init.params()) {
            init = new FxInit(init.id(), init.handleId(), init.origin(), init.target(), init.casterId(), init.followId(),
                    init.follow(), init.seed(), init.duration(), onTime ? init.startAge() : e.ageNow(), e.params);
        }
        Fx fx;
        long t0 = System.nanoTime();
        try {
            fx = factory.create(init);
        } catch (RuntimeException ex) {
            failed++;
            e.broken = true;
            BHFxMod.LOGGER.error("bhfx effect {} failed to construct", init.id(), ex);
            return;
        } finally {
            long dt = System.nanoTime() - t0;
            startsThisTick++;
            constructNanosThisTick += dt;
            if (dt > longestConstructNanos) {
                longestConstructNanos = dt;
                longestConstructId = init.id();
            }
        }
        e.fx = fx;
        e.shownSince = clientTicks;
        if (e.init.duration() == FxSpawn.DEFAULT_DURATION) {
            DEFAULT_TICKS.put(e.init.id(), fx.duration());
        }
        e.duration = fx.duration();
        if (onTime) {
            e.presence = 1f;
            e.fadeStep = 0f;
        } else {
            builtLate++;
            e.presence = 0f;
            e.fadeStep = 1f / FADE_TICKS;
        }
        peakActive = Math.max(peakActive, builtCount());
    }

    /** Free the built effect; the entry stays known and can be built again. */
    private static void unbuild(Entry e) {
        remove(e);
        e.fx = null;
        e.presence = 1f;
        e.fadeStep = 0f;
    }

    private static int builtCount() {
        int n = 0;
        for (Entry e : ACTIVE) {
            if (e.fx != null) {
                n++;
            }
        }
        return n;
    }

    /** Past {@link #MAX_KNOWN}, forget the farthest effects that are not built and not pinned. */
    private static void forgetIfTooMany() {
        while (ACTIVE.size() > MAX_KNOWN) {
            Entry far = null;
            for (Entry e : ACTIVE) {
                if (e.fx == null && !e.pinned && (far == null || e.distance > far.distance)) {
                    far = e;
                }
            }
            if (far == null) {
                return;
            }
            ACTIVE.remove(far);
            forgotten++;
        }
    }

    /** Where the effect is now (its followed entity, else its origin), its distance from the player, and pinned. */
    private static void measure(Minecraft mc, Entry e) {
        ClientLevel level = mc.level;
        Entity follow = level != null && e.init.followId() >= 0 ? level.getEntity(e.init.followId()) : null;
        Vec3 at = follow != null && !follow.isRemoved() ? follow.position() : e.init.origin();
        if (mc.player == null) {
            e.distance = 0.0;
            e.pinned = e.local() && !e.mayHide;
            return;
        }
        int me = mc.player.getId();
        e.distance = mc.player.position().distanceTo(at);
        e.pinned = (e.local() && !e.mayHide) || e.init.casterId() == me || e.init.followId() == me;
    }

    static void stop(int handleId, StopMode mode) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Entry e : ACTIVE) {
            if (e.handleId == handleId) {
                if (e.fx == null) {
                    ACTIVE.remove(e); // not built here: nothing to end
                } else {
                    e.stopping = true;
                    e.fx.bhfxStop(TICK.bind(level, e.fx, followed(level, e.fx)), mode);
                }
                return;
            }
        }
    }

    static void update(int handleId, FxParams changed) {
        for (Entry e : ACTIVE) {
            if (e.handleId == handleId) {
                e.params = e.params.merge(changed);
                if (e.fx == null) {
                    return;
                }
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

    public static boolean isLocalAlive(LocalFxHandle handle) {
        if (handle == null || handle.isNone()) {
            return false;
        }
        for (Entry e : ACTIVE) {
            if (e.handleId == handle.id()) {
                return true;
            }
        }
        return false;
    }

    public static void stopLocal(LocalFxHandle handle, StopMode mode) {
        if (handle != null && !handle.isNone()) {
            stop(handle.id(), mode);
        }
    }

    public static void updateLocal(LocalFxHandle handle, FxParams changed) {
        if (handle != null && !handle.isNone() && changed != null && !changed.isEmpty()) {
            update(handle.id(), changed);
        }
    }

    public static <T extends Fx> Optional<T> localFx(LocalFxHandle handle, Class<T> type) {
        if (handle == null || handle.isNone()) {
            return Optional.empty();
        }
        for (Entry e : ACTIVE) {
            if (e.handleId == handle.id()) {
                return e.fx != null && type.isInstance(e.fx) ? Optional.of(type.cast(e.fx)) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** A screen pulse from the server; {@code at == null} = full strength. */
    static void pulse(@Nullable Vec3 at, float range, ScreenPulse pulse) {
        if (!enabled() || Minecraft.getInstance().level == null) {
            return;
        }
        float weight = 1f;
        if (at != null) {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            weight = (float) Math.max(0.0, 1.0 - camera.distanceTo(at) / Math.max(range, 1.0));
            if (weight <= 0.01f) {
                return;
            }
        }
        if (pulse.shake() > 0f) {
            trauma = Math.min(1f, trauma + pulse.shake() * weight);
        }
        if (pulse.length() > 0) {
            if (PULSES.size() >= MAX_PULSES) {
                PULSES.remove(0);
            }
            PULSES.add(new ActivePulse(pulse, weight));
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
        PULSES.clear();
        trauma = 0f;
        frameShake = 0f;
        resetCounters();
    }

    /** Zero the counters shown by /bhfx stats (world change, and the start of /bhfx stress). */
    static void resetCounters() {
        peakActive = builtCount();
        evicted = failed = unregistered = builtLate = forgotten = 0;
        startsThisTick = busiestTickStarts = 0;
        constructNanosThisTick = busiestTickConstructNanos = longestConstructNanos = 0L;
        longestConstructId = null;
    }

    /** Close the starts of the tick that just ended into the "busiest tick" counters. */
    private static void rollStartCounters() {
        if (startsThisTick > busiestTickStarts
                || (startsThisTick == busiestTickStarts && constructNanosThisTick > busiestTickConstructNanos)) {
            busiestTickStarts = startsThisTick;
            busiestTickConstructNanos = constructNanosThisTick;
        }
        startsThisTick = 0;
        constructNanosThisTick = 0L;
    }

    /** Once a second: count living particles in every batch (walks the filled slots, so not every frame). */
    private static void samplePeaks() {
        for (Entry e : ACTIVE) {
            if (e.fx != null) {
                for (ParticleBatch b : e.fx.bhfxParticleBatches()) {
                    b.samplePeakAlive();
                }
            }
        }
    }

    /** One line: running effects and what was refused or evicted. */
    static String counterLine() {
        return String.format("%d fx known, %d built (peak %d, maxActiveFx %d, radius %d), %d hidden, %d built late, "
                        + "%d failed, %d unregistered%s",
                ACTIVE.size(), builtCount(), peakActive, BHFxConfig.MAX_ACTIVE_FX.get(),
                BHFxConfig.GUARANTEED_RADIUS.get(), evicted, builtLate, failed, unregistered,
                forgotten > 0 ? ", " + forgotten + " forgotten" : "");
    }

    private static void remove(Entry e) {
        if (e.fx == null) {
            return;
        }
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
        rollStartCounters();
        clientTicks++;
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            // respawn / dimension change / disconnect: the server re-sends what is still playing
            clear();
            BENCH.cancel();
            if (lastLevel == null && level != null) {
                scheduleWarmup(); // joined a world
            } else if (level == null) {
                WARMUP.cancel();
                STATS.clear();
            }
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
        BENCH.tick();
        if (!PULSES.isEmpty()) {
            PULSES.forEach(ActivePulse::tick);
            PULSES.removeIf(ActivePulse::finished);
        }
        if (ACTIVE.isEmpty()) {
            releaseIfIdle();
            return;
        }
        long tickStart = System.nanoTime();
        ParticleBatch.clockOffset(1f);
        if (++sampleClock >= 20) {
            sampleClock = 0;
            samplePeaks();
        }

        Iterator<Entry> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            measure(mc, e);
            if (e.fx == null) {
                // known but not built: it only has to notice when it ends
                Entity follow = e.init.followId() >= 0 ? level.getEntity(e.init.followId()) : null;
                boolean present = follow != null && !follow.isRemoved();
                e.followSeen |= present;
                boolean lost = e.init.followId() >= 0 && e.followSeen && !present;
                if (lost || (e.duration >= 0 && e.ageNow() > e.duration)) {
                    it.remove();
                }
                continue;
            }
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
                continue;
            }
            if (e.fadeStep != 0f) {
                e.presence += e.fadeStep;
                if (e.presence >= 1f) {
                    e.presence = 1f;
                    e.fadeStep = 0f;
                } else if (e.presence <= 0f) {
                    unbuild(e); // faded out: free it, keep knowing it
                }
            }
        }
        select();
        long now = System.nanoTime();
        STATS.tick(now, now - tickStart);
        releaseIfIdle();
    }

    /**
     * Decide which known effects are built ({@link FxSelection}), fade out the ones no longer chosen, and build the
     * chosen ones nearest first: close ones at once, the rest within this tick's construction budget.
     */
    private static void select() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        CANDIDATES.clear();
        for (Entry e : ACTIVE) {
            boolean shown = e.fx != null && e.fadeStep >= 0f;
            // local effects are never hidden (callers hold their Fx); effects playing their ending finish where they are
            boolean pinned = e.pinned || (e.fx != null && e.stopping);
            CANDIDATES.add(new FxSelection.Candidate(e, pinned, e.distance, shown, e.shownSince));
        }
        FxSelection.choose(CANDIDATES, new FxSelection.Rules(BHFxConfig.GUARANTEED_RADIUS.get(), MARGIN,
                BHFxConfig.MAX_ACTIVE_FX.get(), BHFxConfig.CULL_DISTANCE.get(), HOLD_TICKS), clientTicks);
        CANDIDATES.sort((a, b) -> Double.compare(((Entry) a.key()).distance, ((Entry) b.key()).distance));
        for (FxSelection.Candidate c : CANDIDATES) {
            Entry e = (Entry) c.key();
            if (c.show()) {
                if (e.fx == null) {
                    if (e.distance <= BUILD_NOW_DISTANCE || constructNanosThisTick < BUILD_BUDGET_NANOS) {
                        build(e);
                    }
                } else if (e.fadeStep < 0f) {
                    e.fadeStep = 1f / FADE_TICKS; // chosen again while fading out: come back
                }
            } else if (e.fx != null && e.fadeStep >= 0f) {
                evicted++;
                e.fadeStep = -1f / FADE_TICKS;
            }
        }
        ACTIVE.removeIf(e -> e.broken);
        CANDIDATES.clear();
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

    /** Called after the solid blocks, while the terrain fog is still set up. */
    static void captureFog() {
        renderer().captureFog();
    }

    static void render(Camera camera, float partial) {
        BENCH.frame(System.nanoTime());
        frameShake = 0f;
        CAMERA.reset();
        if (BENCH.suppressing()) {
            return;
        }
        lastPassCount = 0;
        if (!enabled()) {
            return;
        }
        if (WARMUP.running()) {
            PostRenderer r = renderer();
            if (r.disabled()) {
                WARMUP.cancel();
            } else {
                WARMUP.step(r);
            }
        }
        if ((ACTIVE.isEmpty() && PULSES.isEmpty()) || Minecraft.getInstance().level == null) {
            return;
        }
        long start = System.nanoTime();
        double gpu = renderFrame(camera, partial);
        BENCH.gpu(gpu);
        long end = System.nanoTime();
        STATS.frame(end, end - start, gpu, builtCount(), lastPassCount);
    }

    /** Returns a GPU measurement that finished this frame, or -1. */
    private static double renderFrame(Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        VIEW_PROJ.set(PROJ).mul(VIEW);
        Vec3 cam = camera.getPosition();
        FRAME.begin(partial, cam, mc.getMainRenderTarget().width, mc.getMainRenderTarget().height, VIEW_PROJ, PROJ.m11());
        PASSES.clear(cam);
        ParticleBatch.clockOffset(partial);
        SCREEN.reset();
        double cullCap = BHFxConfig.CULL_DISTANCE.get();
        for (Entry e : ACTIVE) {
            if (e.broken || e.fx == null) {
                continue;
            }
            FRAME.bind(e.fx, followed(level, e.fx));
            double distance = e.fx.bhfxFocus(FRAME).distanceTo(cam);
            if (distance > Math.min(e.fx.bhfxCullDistance(), cullCap)) {
                continue;
            }
            PASSES.currentDistance(distance);
            PASSES.currentPresence(e.presenceAt(partial));
            try {
                e.fx.bhfxFrame(FRAME, PASSES, SCREEN, CAMERA.bindFor(isLocalCaster(mc, e.fx)));
            } catch (RuntimeException ex) {
                BHFxMod.LOGGER.error("bhfx effect {} failed while rendering; removing it", e.fx.init().id(), ex);
                e.broken = true;
            }
        }
        for (ActivePulse p : PULSES) {
            p.apply(SCREEN, partial);
        }
        frameShake = SCREEN.shakeValue();
        if ((PASSES.isEmpty() && SCREEN.imageIdle()) || ShaderpackDetector.blocksPost()) {
            return -1;
        }
        PostRenderer r = renderer();
        if (r.disabled()) {
            return -1;
        }
        List<PassListImpl.Entry> draw = PASSES.drawList(BHFxConfig.MAX_PASSES_PER_FRAME.get());
        float time = ((level.getGameTime() % 24000L) + partial) / 20f;
        r.render(draw, SCREEN, PROJ, VIEW, cam, time, effectiveQuality());
        lastPassCount = r.lastPassCount();
        lastPostNanos = System.nanoTime();
        checkBudget(r, lastPostNanos);
        return r.takeGpuSample();
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
        // read the average before resetBudget() clears it
        double usedMs = r.averageGpuMs();
        qualityCeiling = current.lower();
        overBudgetSince = 0L;
        r.resetBudget();
        String msg = String.format("[bhfx] effects used %.1f ms of GPU per frame (budget %.1f ms); quality lowered to %s for this session.",
                usedMs, budget, qualityCeiling);
        BHFxMod.LOGGER.warn(msg);
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal(msg).withStyle(ChatFormatting.GOLD));
    }

    /** Lines for the F3 screen. */
    static void debugLines(List<String> out) {
        if (!enabled()) {
            out.add("[bhfx] off");
            return;
        }
        out.add("[bhfx] " + builtCount() + "/" + ACTIVE.size() + " fx, " + lastPassCount + " pass, " + effectiveQuality()
                + (ShaderpackDetector.blocksPost() ? ", post off (shader pack)" : "")
                + (renderer != null ? ", " + renderer.describe() : "")
                + (WARMUP.running() ? ", " + WARMUP.describe() : ""));
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
            if (!e.broken && e.fx != null) {
                e.fx.bhfxHud(graphics, FRAME.bind(e.fx, followed(level, e.fx)));
            }
        }
    }

    private static boolean isLocalCaster(Minecraft mc, Fx fx) {
        return mc.player != null && fx.init().casterId() >= 0 && fx.init().casterId() == mc.player.getId();
    }

    /** Camera requests gathered last frame. */
    static CameraParamsImpl camera() {
        return CAMERA;
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
        if (ShaderpackDetector.blocksPost()) {
            sb.append(", post off while a shader pack is active (compat.offWithShaderpack)");
        } else if (ShaderpackDetector.packActive()) {
            sb.append(", shader pack active (post forced on)");
        }
        sb.append(", ").append(builtCount()).append(" fx built of ").append(ACTIVE.size()).append(" known, ")
                .append(lastPassCount).append(" pass(es) last frame");
        if (renderer != null) {
            sb.append(", ").append(renderer.describe());
        }
        sb.append(", ").append(FACTORIES.size()).append(" registered");
        if (WARMUP.running()) {
            sb.append(", ").append(WARMUP.describe());
        }
        return sb.toString();
    }

    static List<String> statsLines() {
        List<String> lines = new ArrayList<>(List.of(STATS.report(System.nanoTime())));
        String held = renderer != null && renderer.allocated()
                ? String.format("  GPU memory: ~%d MB held, effect resolution %d%% of budget", renderer.heldBytes() >> 20,
                Math.round(renderer.resolutionShare() * 100))
                : "  GPU memory: none held";
        lines.add(held + ", quality " + effectiveQuality());
        lines.add("  Effects: " + counterLine());
        lines.add(String.format("  Starts: busiest tick %d start(s), %.2f ms constructing; slowest constructor %.2f ms%s",
                Math.max(busiestTickStarts, startsThisTick),
                Math.max(busiestTickConstructNanos, constructNanosThisTick) / 1e6, longestConstructNanos / 1e6,
                longestConstructId != null ? " (" + longestConstructId + ")" : ""));
        particleLines(lines);
        return lines;
    }

    private record BatchRow(ResourceLocation fx, ResourceLocation material, int peak, int capacity) {
    }

    /** Particle slots held by the running effects, and the batches whose capacity is far from what they use. */
    private static void particleLines(List<String> lines) {
        List<BatchRow> rows = new ArrayList<>();
        long slots = 0;
        long bytes = 0;
        for (Entry e : ACTIVE) {
            if (e.fx == null) {
                continue;
            }
            for (ParticleBatch b : e.fx.bhfxParticleBatches()) {
                if (b.closed()) {
                    continue;
                }
                b.samplePeakAlive();
                slots += b.capacity();
                bytes += b.heapBytes();
                rows.add(new BatchRow(e.fx.init().id(), b.material(), b.peakAlive(), b.capacity()));
            }
        }
        if (rows.isEmpty()) {
            lines.add("  Particles: no batches");
            return;
        }
        lines.add(String.format("  Particles: %d batch(es), %d slot(s) allocated, ~%.1f MB heap (the same on the GPU once drawn)",
                rows.size(), slots, bytes / 1048576.0));
        rows.sort((a, b) -> Integer.compare(b.capacity(), a.capacity()));
        StringBuilder sb = new StringBuilder("  Biggest (peak alive / capacity):");
        for (int i = 0; i < Math.min(5, rows.size()); i++) {
            BatchRow r = rows.get(i);
            sb.append(i == 0 ? " " : "; ").append(r.fx().getPath()).append('/').append(r.material().getPath())
                    .append(' ').append(r.peak()).append('/').append(r.capacity());
        }
        lines.add(sb.toString());
        int oversized = 0;
        for (BatchRow r : rows) {
            if (r.capacity() >= 256 && r.peak() * 4 < r.capacity()) {
                oversized++;
            }
        }
        if (oversized > 0) {
            lines.add("  " + oversized + " batch(es) never above 25% of their capacity (could be made smaller)");
        }
    }

    /** Start /bhfx bench. Returns an error message, or null when it started. */
    @Nullable
    static String startBench(ResourceLocation fxId, int seconds) {
        if (!enabled()) {
            return "bhfx is off";
        }
        if (!FACTORIES.containsKey(fxId)) {
            return fxId + " is not a registered effect";
        }
        if (BENCH.running()) {
            return "a bench is already running";
        }
        BENCH.start(fxId, seconds);
        return null;
    }

    /** Ids of every registered effect. Also read by the integrated server for /bhfx_play suggestions. */
    public static Set<ResourceLocation> registeredIds() {
        return FACTORIES.keySet();
    }
}
