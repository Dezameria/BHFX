package net.offkung.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Impact frames flipped like anime cels: each style holds for {@code frameSeconds}; a {@code null} entry is a gap.
 * <pre>{@code
 * static final ImpactSequence HIT = ImpactSequence.of(0.05f, ImpactStyle.ink(0x000000, 0xFFFFFF), null,
 *         ImpactStyle.inverted(0x000000, 0xFFFFFF));
 * HIT.apply(screen, secondsSinceHit, 1f, hitPos);
 * }</pre>
 */
public final class ImpactSequence {
    private final float frameSeconds;
    private final ImpactStyle[] frames;

    private ImpactSequence(float frameSeconds, ImpactStyle[] frames) {
        this.frameSeconds = Math.max(frameSeconds, 1e-3f);
        this.frames = frames;
    }

    public static ImpactSequence of(float frameSeconds, @Nullable ImpactStyle... frames) {
        return new ImpactSequence(frameSeconds, Arrays.copyOf(frames, frames.length));
    }

    public float lengthSeconds() {
        return frameSeconds * frames.length;
    }

    /** Applies the frame for {@code secondsSince} (nothing before 0, after the end, or on a gap). */
    public void apply(ScreenParams screen, float secondsSince, float strength, Vec3 focus) {
        if (secondsSince < 0f || strength <= 0f) {
            return;
        }
        int index = (int) (secondsSince / frameSeconds);
        if (index >= frames.length || frames[index] == null) {
            return;
        }
        screen.impact(frames[index], strength, focus);
    }
}
