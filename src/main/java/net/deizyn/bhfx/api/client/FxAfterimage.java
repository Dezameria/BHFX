package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Afterimages of a moving model: remembers where the model was (its frame, scale and pose) every {@code interval}
 * seconds and draws a fading ghost at each of the last {@code copies} places, so a fast swing or dash leaves a trail
 * of itself behind.
 * <pre>{@code
 * private final FxAfterimage trail = new FxAfterimage(6, 0.04f);
 * // passes(), every frame:
 * float t = seconds(frame);
 * trail.record(t, held, 1f, pose);                       // where the model is now
 * trail.drawGlow(out, SWORD, t, 0x66C8FF, 1.2f);         // the ghosts, oldest dimmest
 * out.model(SWORD, held, 1f, pose, FxModel.TEXTURED, u -> {});   // the model itself, on top
 * }</pre>
 * Each ghost is one ordinary model draw with its own pose, so keep {@code copies} small (4 to 10). A ghost fades
 * evenly over {@code copies * interval} seconds from the moment it was recorded; nothing is recorded while the model
 * stands still, so a resting model has no ghosts stacked on it once the old ones have faded.
 */
@ApiStatus.Experimental
public final class FxAfterimage {
    /** Sets a ghost's uniforms. {@code fade}: 1 just recorded .. 0 gone; {@code index}: 0 = newest. */
    @FunctionalInterface
    public interface Ghost {
        void set(Uniforms u, float fade, int index);
    }

    private final int copies;
    private final float interval;
    private final FxFrame[] frames;
    private final float[] scales;
    private final float[] times;
    private final FxPose[] poses;
    private final boolean[] posed;
    private int head = -1;
    private int count;

    /** {@code copies}: ghosts kept, 1..32; {@code interval}: seconds between them (at least 0.005). */
    public FxAfterimage(int copies, float interval) {
        this.copies = Math.max(1, Math.min(32, copies));
        this.interval = Math.max(0.005f, interval);
        this.frames = new FxFrame[this.copies];
        this.scales = new float[this.copies];
        this.times = new float[this.copies];
        this.poses = new FxPose[this.copies];
        this.posed = new boolean[this.copies];
    }

    /** How long a ghost lasts, in seconds. */
    public float span() {
        return copies * interval;
    }

    /** Ghosts currently remembered (some may already have faded). */
    public int count() {
        return count;
    }

    /**
     * Tell the trail where the model is now; call every frame with the effect's {@code seconds(frame)}. A ghost is
     * kept when {@code interval} has passed since the last one and the model has moved or turned since then.
     * {@code pose} is copied, so the same object can go on changing.
     */
    public void record(float seconds, FxFrame frame, float scale, @Nullable FxPose pose) {
        if (frame == null) {
            return;
        }
        if (head >= 0) {
            float since = seconds - times[head];
            if (since < 0f) {
                clear(); // the clock went back (the effect restarted)
            } else if (since < interval || same(frames[head], frame)) {
                return;
            }
        }
        head = (head + 1) % copies;
        count = Math.min(copies, count + 1);
        frames[head] = frame;
        scales[head] = scale;
        times[head] = seconds;
        posed[head] = pose != null && !pose.isEmpty();
        if (posed[head]) {
            if (poses[head] == null) {
                poses[head] = new FxPose();
            }
            poses[head].set(pose);
        }
    }

    private static boolean same(FxFrame a, FxFrame b) {
        return a.origin().distanceToSqr(b.origin()) < 1e-6 && a.x().distanceToSqr(b.x()) < 1e-6
                && a.y().distanceToSqr(b.y()) < 1e-6;
    }

    /** Forget every ghost (after a teleport, or when the model is put away). */
    public void clear() {
        head = -1;
        count = 0;
    }

    /**
     * Draw the ghosts that have not faded yet, oldest first. {@code setup} sets each one's uniforms from its fade
     * (for example {@code MColor}'s alpha for a textured material, its brightness for a glowing one).
     */
    public void draw(PassList out, FxModel model, ResourceLocation material, PassOptions options, float seconds,
                     Ghost setup) {
        float span = span();
        for (int n = count - 1; n >= 0; n--) {
            int i = Math.floorMod(head - n, copies);
            float fade = 1f - (seconds - times[i]) / span;
            if (!(fade > 0.001f) || fade > 1f) {
                continue;
            }
            int index = n;
            out.model(model, frames[i], scales[i], posed[i] ? poses[i] : null, material, options,
                    u -> setup.set(u, fade, index));
        }
    }

    /**
     * The ghosts as shells of light ({@link FxModel#GLOW}) in {@code rgb} (0xRRGGBB), {@code strength} bright when
     * new and fading to nothing.
     */
    public void drawGlow(PassList out, FxModel model, float seconds, int rgb, float strength) {
        float r = linear(rgb >> 16) * strength;
        float g = linear(rgb >> 8) * strength;
        float b = linear(rgb) * strength;
        draw(out, model, FxModel.GLOW, PassOptions.PARTICLES, seconds,
                (u, fade, index) -> u.f4("MColor", r * fade * fade, g * fade * fade, b * fade * fade, 1f)
                        .f4("MParams", 0f, 0f, 0f, 0f));
    }

    private static float linear(int channel) {
        return (float) Math.pow((channel & 0xFF) / 255.0, 2.2);
    }
}
