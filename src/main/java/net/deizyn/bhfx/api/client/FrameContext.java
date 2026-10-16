package net.offkung.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.Optional;

/** Per-frame data for {@code passes()} and {@code screen()}. Valid only during the call. */
public interface FrameContext {
    float partialTick();

    Vec3 cameraPos();

    /** world - camera, the space every bhfx shader works in. */
    Vec3 rel(Vec3 world);

    /** The effect's interpolated position (followed entity or origin). */
    Vec3 position();

    /** Screen uv (0..1, origin bottom-left) of a world point; empty when behind the camera. */
    Optional<Vector2f> screenUv(Vec3 world);

    int width();

    int height();
}
