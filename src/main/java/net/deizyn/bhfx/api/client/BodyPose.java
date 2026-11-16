package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;

/**
 * Where a {@link net.deizyn.bhfx.api.BodyPart} is right now, in world space, with the part's own axes (unit vectors).
 *
 * @param position world position of the point
 * @param forward  the way the part faces (for the head: where the face looks)
 * @param up       the part's up; for arms and legs this runs from the tip back to the joint, so a hanging arm points up
 * @param right    the entity's right as carried by the part
 * @param exact    true when it comes from the posed model this frame; false when estimated (entity not drawn, first
 *                 person, or a model bhfx does not know)
 */
public record BodyPose(Vec3 position, Vec3 forward, Vec3 up, Vec3 right, boolean exact) {
    /** {@code position + right * x + up * y + forward * z}: a point in the part's own frame. */
    public Vec3 local(double x, double y, double z) {
        return position.add(right.x * x + up.x * y + forward.x * z, right.y * x + up.y * y + forward.y * z,
                right.z * x + up.z * y + forward.z * z);
    }
}
