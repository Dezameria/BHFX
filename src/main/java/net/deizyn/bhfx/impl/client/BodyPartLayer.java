package net.deizyn.bhfx.impl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.deizyn.bhfx.api.BodyPart;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import static net.deizyn.bhfx.impl.client.PlayerAnimCompat.ARM_JOINT;
import static net.deizyn.bhfx.impl.client.PlayerAnimCompat.JOINT;

/**
 * Draws nothing: reads where the posed model's parts are, for {@link BodyParts}. Added to every living-entity renderer.
 * A layer runs after {@code setupAnim} with the pose stack already in model space (y down, 1/16 block per pixel), so
 * it needs no mixin. Humanoid models give every part; other models give the head ({@link HeadedModel}) and hands
 * ({@link ArmedModel}), the rest is estimated.
 * <p>
 * playerAnimator (Emotecraft, Iron's Spells cast poses) poses the parts and the whole body before layers run, so those
 * are read as they are. Its bends (bendy-lib: elbows, knees, torso) are drawn inside the model render only; they are
 * added here through {@link PlayerAnimCompat}: points at or below a joint turn round it, a point on the joint by half.
 */
final class BodyPartLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final float[] bends = new float[PlayerAnimCompat.SIZE];

    BodyPartLayer(RenderLayerParent<T, M> parent) {
        super(parent);
        PlayerAnimCompat.claimLayer(this);
    }

    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!BodyParts.wants(entity)) {
            return;
        }
        M model = getParentModel();
        if (model instanceof HumanoidModel<?> h) {
            boolean bent = PlayerAnimCompat.bends(entity, bends);
            // upper body: the torso bend turns head and arms round the waist; the body point sits on it
            stack.pushPose();
            if (bent) {
                PlayerAnimCompat.bend(stack, bends, PlayerAnimCompat.BODY, JOINT, 1f);
            }
            at(stack, entity, h.head, BodyPart.HEAD, 0f, -4f, bent, -1, 0f, 0f);
            at(stack, entity, h.leftArm, BodyPart.LEFT_SHOULDER, 1f, -2f, bent, -1, 0f, 0f);
            at(stack, entity, h.rightArm, BodyPart.RIGHT_SHOULDER, -1f, -2f, bent, -1, 0f, 0f);
            at(stack, entity, h.leftArm, BodyPart.LEFT_ARM, 1f, 4f, bent, PlayerAnimCompat.LEFT_ARM, ARM_JOINT, 0.5f);
            at(stack, entity, h.rightArm, BodyPart.RIGHT_ARM, -1f, 4f, bent, PlayerAnimCompat.RIGHT_ARM, ARM_JOINT, 0.5f);
            at(stack, entity, h.leftArm, BodyPart.LEFT_HAND, 1f, 10f, bent, PlayerAnimCompat.LEFT_ARM, ARM_JOINT, 1f);
            at(stack, entity, h.rightArm, BodyPart.RIGHT_HAND, -1f, 10f, bent, PlayerAnimCompat.RIGHT_ARM, ARM_JOINT, 1f);
            stack.popPose();
            at(stack, entity, h.body, BodyPart.BODY, 0f, 6f, bent, PlayerAnimCompat.BODY, JOINT, 0.5f);
            // legs: no torso bend; the knee bends the shin
            at(stack, entity, h.leftLeg, BodyPart.LEFT_LEG, 0f, 6f, bent, PlayerAnimCompat.LEFT_LEG, JOINT, 0.5f);
            at(stack, entity, h.rightLeg, BodyPart.RIGHT_LEG, 0f, 6f, bent, PlayerAnimCompat.RIGHT_LEG, JOINT, 0.5f);
            at(stack, entity, h.leftLeg, BodyPart.LEFT_FOOT, 0f, 12f, bent, PlayerAnimCompat.LEFT_LEG, JOINT, 1f);
            at(stack, entity, h.rightLeg, BodyPart.RIGHT_FOOT, 0f, 12f, bent, PlayerAnimCompat.RIGHT_LEG, JOINT, 1f);
            return;
        }
        if (model instanceof HeadedModel headed) {
            at(stack, entity, headed.getHead(), BodyPart.HEAD, 0f, -4f, false, -1, 0f, 0f);
        }
        if (model instanceof ArmedModel armed) {
            hand(stack, entity, armed, HumanoidArm.LEFT, BodyPart.LEFT_HAND, 1f);
            hand(stack, entity, armed, HumanoidArm.RIGHT, BodyPart.RIGHT_HAND, -1f);
        }
    }

    /**
     * Reports the point ({@code x}, {@code y}) pixels in {@code part}'s space. With {@code bent}, first turns it by
     * {@code share} of {@code bendPart}'s bend round the joint {@code joint} blocks down the part ({@code bendPart} -1:
     * none).
     */
    private void at(PoseStack stack, LivingEntity entity, ModelPart part, BodyPart which, float x, float y,
                    boolean bent, int bendPart, float joint, float share) {
        stack.pushPose();
        part.translateAndRotate(stack);
        if (bent && bendPart >= 0) {
            PlayerAnimCompat.bend(stack, bends, bendPart, joint, share);
        }
        stack.translate(x / 16f, y / 16f, 0f);
        BodyParts.report(entity, which, stack.last().pose(), false);
        stack.popPose();
    }

    private static void hand(PoseStack stack, LivingEntity entity, ArmedModel model, HumanoidArm arm, BodyPart which,
                             float x) {
        stack.pushPose();
        model.translateToHand(arm, stack);
        stack.translate(x / 16f, 10f / 16f, 0f);
        BodyParts.report(entity, which, stack.last().pose(), false);
        stack.popPose();
    }
}
