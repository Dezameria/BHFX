package net.deizyn.bhfx.api.client;

import net.deizyn.bhfx.api.BodyPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
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

    /** The effect's interpolated position: the followed body part or entity, else the origin. */
    Vec3 position();

    /**
     * Where {@code part} of the followed entity is this frame; empty when the effect follows nothing or the entity is
     * gone. The first ask about an entity may be an estimate; from the next frame on it follows the posed model.
     */
    Optional<BodyPose> part(BodyPart part);

    /** Where {@code part} of any entity is this frame (see {@link #part(BodyPart)}). */
    BodyPose part(Entity entity, BodyPart part);

    /**
     * Named point {@code name} on the followed entity (a GeckoLib bone, a weapon tip), as its renderer reported it in
     * the last few frames ({@link BHFxClient#reportPoint}); empty when nothing reported it. The first ask may be empty:
     * asking is what makes renderers start reporting the entity.
     */
    @ApiStatus.Experimental
    Optional<BodyPose> point(String name);

    /** Like {@link #point(String)}, on any entity. */
    @ApiStatus.Experimental
    Optional<BodyPose> point(Entity entity, String name);

    /** Every named point the followed entity reported in the last few frames (empty when it follows nothing). */
    @ApiStatus.Experimental
    java.util.Map<String, BodyPose> points();

    /** Screen uv (0..1, origin bottom-left) of a world point; empty when behind the camera. */
    Optional<Vector2f> screenUv(Vec3 world);

    /** Size in pixels of the target passes draw into (may be below the window size). */
    int width();

    /** See {@link #width()}. */
    int height();
}
