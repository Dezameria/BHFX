package net.deizyn.bhfx.api.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A camera position (world space) and rotation in degrees, Minecraft conventions (yaw 0 = +Z, pitch + = down). */
public record CameraPose(Vec3 position, float yaw, float pitch, float roll) {

    public static CameraPose of(Vec3 position, float yaw, float pitch) {
        return new CameraPose(position, yaw, pitch, 0f);
    }

    /** At {@code from}, looking at {@code at}. */
    public static CameraPose lookAt(Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG);
        return new CameraPose(from, yaw, pitch, 0f);
    }

    public CameraPose withRoll(float degrees) {
        return new CameraPose(position, yaw, pitch, degrees);
    }
}
