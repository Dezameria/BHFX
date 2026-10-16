package net.offkung.bhfx.api.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhfx.api.FxParams;
import net.offkung.bhfx.api.FxSpawn;
import net.offkung.bhfx.api.StopMode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Base class of every client-side effect. Override the hooks you need:
 * <ul>
 *   <li>{@link #tick} — 20/s: particles, sounds, shakes, Effekseer</li>
 *   <li>{@link #passes} — per frame: shader passes</li>
 *   <li>{@link #screen} — per frame: full-screen parameters</li>
 * </ul>
 * Time comes only from {@link #age()} / {@link #ticks(FrameContext)}, and randomness only from {@link #random()},
 * so every client shows the same thing.
 */
public abstract class Fx {
    private final FxInit init;
    private final RandomSource random;
    private final float seedF;
    private FxParams params;
    private int age;
    private boolean started;
    @Nullable
    private StopMode stopMode;
    private int stopAge;
    private int endTicks;

    protected Fx(FxInit init) {
        this.init = init;
        this.random = RandomSource.create(init.seed());
        this.seedF = (float) ((init.seed() & 0xFFFFL) / 65535.0);
        this.params = init.params();
        this.age = Math.max(0, init.startAge());
    }

    // ------------------------------------------------------------------ data

    public final FxInit init() {
        return init;
    }

    protected final RandomSource random() {
        return random;
    }

    /** The seed as 0..1, handy as a shader uniform. */
    protected final float seedF() {
        return seedF;
    }

    protected final FxParams params() {
        return params;
    }

    // ------------------------------------------------------------------ time

    /** Ticks since the effect started (including ticks missed before this client received it). */
    public final int age() {
        return age;
    }

    /** age + partial tick, for smooth per-frame animation. */
    protected final float ticks(FrameContext frame) {
        return age + frame.partialTick();
    }

    protected final float seconds(FrameContext frame) {
        return ticks(frame) / 20f;
    }

    protected final boolean stopping() {
        return stopMode != null;
    }

    @Nullable
    protected final StopMode stopMode() {
        return stopMode;
    }

    /** 0..1 through the ending after a stop; 0 while not stopping. */
    protected final float stopProgress(FrameContext frame) {
        if (stopMode == null) {
            return 0f;
        }
        if (endTicks <= 0) {
            return 1f;
        }
        return Math.min(1f, (stopAge + frame.partialTick()) / endTicks);
    }

    /** Ticks this effect lives when the spawn did not set a duration. */
    protected int defaultDuration() {
        return 200;
    }

    /** Effective duration in ticks; {@link FxSpawn#UNTIL_STOPPED} when it only ends by a stop. */
    public final int duration() {
        return init.duration() == FxSpawn.DEFAULT_DURATION ? defaultDuration() : init.duration();
    }

    /** Length of the ending after a stop. */
    protected int endTicks(StopMode mode) {
        return switch (mode) {
            case FINISH -> 10;
            case CANCEL -> 5;
            case IMMEDIATE -> 0;
        };
    }

    // ------------------------------------------------------------------ hooks

    /** Once, before the first tick. Skipped when this client joined mid-effect (startAge > 0). */
    protected void onStart(FxContext ctx) {
    }

    protected void tick(FxContext ctx, int age) {
    }

    protected void onStop(FxContext ctx, StopMode mode) {
    }

    /** After the server sent new values (already merged into {@link #params()}); {@code changed} holds only the new ones. */
    protected void onParamsChanged(FxParams changed) {
    }

    /** Release anything the effect holds (looping sounds, Effekseer emitters). */
    protected void onRemoved() {
    }

    protected void passes(FrameContext frame, PassList out) {
    }

    protected void screen(FrameContext frame, ScreenParams screen) {
    }

    protected void hud(GuiGraphics graphics, FrameContext frame) {
    }

    /** Point used for distance culling. */
    protected Vec3 focus(FrameContext frame) {
        return init.followId() >= 0 ? frame.position() : init.target();
    }

    /** Beyond this the effect still ticks but draws nothing; capped by the player's cull distance. */
    protected double cullDistance() {
        return 256.0;
    }

    // ------------------------------------------------------------------ driven by bhfx

    @ApiStatus.Internal
    public final void bhfxTick(FxContext ctx) {
        if (!started) {
            started = true;
            if (init.startAge() <= 0) {
                onStart(ctx);
            }
        }
        tick(ctx, age);
        age++;
        if (stopMode != null) {
            stopAge++;
        }
    }

    @ApiStatus.Internal
    public final void bhfxStop(FxContext ctx, StopMode mode) {
        if (stopMode != null && mode != StopMode.IMMEDIATE) {
            return;
        }
        stopMode = mode;
        stopAge = 0;
        endTicks = Math.max(0, endTicks(mode));
        onStop(ctx, mode);
    }

    @ApiStatus.Internal
    public final boolean bhfxFinished() {
        if (stopMode != null) {
            return stopAge >= endTicks;
        }
        int d = duration();
        return d >= 0 && age > d;
    }

    @ApiStatus.Internal
    public final void bhfxParams(FxParams changed) {
        params = params.merge(changed);
        onParamsChanged(changed);
    }

    @ApiStatus.Internal
    public final void bhfxRemoved() {
        onRemoved();
    }

    @ApiStatus.Internal
    public final void bhfxFrame(FrameContext frame, PassList passes, ScreenParams screen) {
        passes(frame, passes);
        screen(frame, screen);
    }

    @ApiStatus.Internal
    public final void bhfxHud(GuiGraphics graphics, FrameContext frame) {
        hud(graphics, frame);
    }

    @ApiStatus.Internal
    public final Vec3 bhfxFocus(FrameContext frame) {
        return focus(frame);
    }

    @ApiStatus.Internal
    public final double bhfxCullDistance() {
        return cullDistance();
    }
}
