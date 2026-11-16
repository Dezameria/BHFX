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

/**
 * Draws nothing: reads where the posed model's parts are, for {@link BodyParts}. Added to every living-entity renderer.
 * A layer runs after {@code setupAnim} with the pose stack already in model space (y down, 1/16 block per pixel), so
 * it needs no mixin. Humanoid models give every part; other models give the head ({@link HeadedModel}) and hands
 * ({@link ArmedModel}), the rest is estimated.
 */
final class BodyPartLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    BodyPartLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!BodyParts.wants(entity)) {
            return;
        }
        M model = getParentModel();
        if (model instanceof HumanoidModel<?> h) {
            at(stack, entity, h.head, BodyPart.HEAD, 0f, -4f);
            at(stack, entity, h.body, BodyPart.BODY, 0f, 6f);
            at(stack, entity, h.leftArm, BodyPart.LEFT_SHOULDER, 1f, -2f);
            at(stack, entity, h.rightArm, BodyPart.RIGHT_SHOULDER, -1f, -2f);
            at(stack, entity, h.leftArm, BodyPart.LEFT_ARM, 1f, 4f);
            at(stack, entity, h.rightArm, BodyPart.RIGHT_ARM, -1f, 4f);
            at(stack, entity, h.leftArm, BodyPart.LEFT_HAND, 1f, 10f);
            at(stack, entity, h.rightArm, BodyPart.RIGHT_HAND, -1f, 10f);
            at(stack, entity, h.leftLeg, BodyPart.LEFT_LEG, 0f, 6f);
            at(stack, entity, h.rightLeg, BodyPart.RIGHT_LEG, 0f, 6f);
            at(stack, entity, h.leftLeg, BodyPart.LEFT_FOOT, 0f, 12f);
            at(stack, entity, h.rightLeg, BodyPart.RIGHT_FOOT, 0f, 12f);
            return;
        }
        if (model instanceof HeadedModel headed) {
            at(stack, entity, headed.getHead(), BodyPart.HEAD, 0f, -4f);
        }
        if (model instanceof ArmedModel armed) {
            hand(stack, entity, armed, HumanoidArm.LEFT, BodyPart.LEFT_HAND, 1f);
            hand(stack, entity, armed, HumanoidArm.RIGHT, BodyPart.RIGHT_HAND, -1f);
        }
    }

    private static void at(PoseStack stack, LivingEntity entity, ModelPart part, BodyPart which, float x, float y) {
        stack.pushPose();
        part.translateAndRotate(stack);
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
