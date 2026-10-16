package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.BHFxMod;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/** Moves the camera for {@code CameraParams.override}. Reflection keeps bhfx free of mixins and access transformers. */
final class CameraMover {
    @Nullable
    private static Method setPosition;
    private static boolean failed;

    private CameraMover() {
    }

    static void move(Camera camera, Vec3 to) {
        if (failed) {
            return;
        }
        try {
            if (setPosition == null) {
                // Camera#setPosition(double, double, double)
                setPosition = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90584_", double.class, double.class, double.class);
            }
            setPosition.invoke(camera, to.x, to.y, to.z);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            BHFxMod.LOGGER.warn("bhfx cannot move the camera; camera overrides will only turn it", e);
        }
    }
}
