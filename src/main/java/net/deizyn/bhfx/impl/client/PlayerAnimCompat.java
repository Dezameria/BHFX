package net.deizyn.bhfx.impl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.deizyn.bhfx.BHFxMod;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Optional playerAnimator support for body parts: elbow, knee and torso bends (bendy-lib). Without playerAnimator, or
 * when a version doesn't link, every call does nothing. playerAnimator classes are touched only in
 * {@link PlayerAnimBends}.
 */
final class PlayerAnimCompat {
    static final int BODY = 0;
    static final int RIGHT_ARM = 1;
    static final int LEFT_ARM = 2;
    static final int RIGHT_LEG = 3;
    static final int LEFT_LEG = 4;
    /** (axis, angle) per part, in the order above. */
    static final int SIZE = 10;

    /** Elbow sits 4 pixels below the shoulder pivot (playerAnimator's held-item bend). */
    static final float ARM_JOINT = 4f / 16f;
    /** Knee and waist: half of a 12-pixel leg or torso (playerAnimator's {@code IBendHelper.rotateMatrixStack}). */
    static final float JOINT = 6f / 16f;

    private static boolean probed;
    private static boolean present;

    private static final Quaternionf Q = new Quaternionf();
    private static final Vector3f AXIS = new Vector3f();

    private PlayerAnimCompat() {
    }

    private static boolean present() {
        if (!probed) {
            probed = true;
            present = ModList.get().isLoaded("playeranimator");
        }
        return present;
    }

    private static void disable(LinkageError e) {
        present = false;
        BHFxMod.LOGGER.warn("bhfx: playerAnimator support off, this version does not link: {}", e.toString());
    }

    /** Call once per {@link BodyPartLayer} built. */
    static void claimLayer(RenderLayer<?, ?> layer) {
        if (!present()) {
            return;
        }
        try {
            PlayerAnimBends.notUpperPart(layer);
        } catch (LinkageError e) {
            disable(e);
        }
    }

    /** True and {@code out} filled when {@code entity} plays a playerAnimator animation with bends on. */
    static boolean bends(LivingEntity entity, float[] out) {
        if (!present()) {
            return false;
        }
        try {
            return PlayerAnimBends.read(entity, out);
        } catch (LinkageError e) {
            disable(e);
            return false;
        }
    }

    /** Dev only: {@link PlayerAnimBends#testPose}; returns a line for chat. */
    static String testPose(boolean on, boolean side) {
        if (!present()) {
            return "playerAnimator is not loaded";
        }
        try {
            return PlayerAnimBends.testPose(on, side);
        } catch (LinkageError | RuntimeException e) {
            return "failed: " + e;
        }
    }

    /**
     * Bends the stack the way playerAnimator bends that part: rotate by {@code share} of the bend angle round an axis
     * in the part's xz plane, through the joint {@code joint} blocks down the part. Same maths as
     * {@code IBendHelper.rotateMatrixStack}.
     */
    static void bend(PoseStack stack, float[] bends, int part, float joint, float share) {
        float axis = -bends[part * 2];
        float angle = bends[part * 2 + 1] * share;
        if (angle == 0f) {
            return;
        }
        stack.translate(0f, joint, 0f);
        stack.mulPose(Q.identity().rotateAxis(angle, AXIS.set((float) Math.cos(axis), 0f, (float) Math.sin(axis))));
        stack.translate(0f, -joint, 0f);
    }
}
