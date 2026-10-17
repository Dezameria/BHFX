package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.Optional;

/** Per-frame data for {@code passes()} and {@code screen()}. Valid only during the call. */
public interface FrameContext {
    /** Fraction of the current tick, 0..1, for smooth motion between ticks. */
    float partialTick();

    /** Camera position in world space. */
    Vec3 cameraPos();

    /** world - camera, the space every bhfx shader works in. */
    Vec3 rel(Vec3 world);

    /** The effect's interpolated position (followed entity or origin). */
    Vec3 position();

    /** Screen uv (0..1, origin bottom-left) of a world point; empty when behind the camera. */
    Optional<Vector2f> screenUv(Vec3 world);

    /** Size in pixels of the target passes draw into (may be below the window size). */
    int width();

    /** See {@link #width()}. */
    int height();
}
