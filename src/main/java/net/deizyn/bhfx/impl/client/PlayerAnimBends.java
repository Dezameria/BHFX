package net.deizyn.bhfx.impl.client;

import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.AnimationFormat;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Pair;
import dev.kosmx.playerAnim.impl.Helper;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.IUpperPartHelper;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The only class that touches playerAnimator (Emotecraft, Iron's Spells cast poses). Loaded only through
 * {@link PlayerAnimCompat}, so bhfx runs without it. Reads the bends playerAnimator draws with bendy-lib: the arm,
 * leg and torso rotations themselves already sit on the model parts and the pose stack when {@link BodyPartLayer}
 * runs, so only the bends are missing there.
 */
final class PlayerAnimBends {
    private static final String[] NAMES = {"body", "rightArm", "leftArm", "rightLeg", "leftLeg"};

    private PlayerAnimBends() {
    }

    /**
     * playerAnimator gives every layer the torso bend unless the layer says it is not an "upper part"; bhfx's layer
     * reads legs too, so it takes no torso bend and adds it itself to the upper points.
     */
    static void notUpperPart(RenderLayer<?, ?> layer) {
        if ((Object) layer instanceof IUpperPartHelper helper) {
            helper.setUpperPart(false);
        }
    }

    /**
     * Fills {@code out} (axis, angle in radians) for body, right arm, left arm, right leg, left leg, in
     * {@link PlayerAnimCompat}'s order. False when bends are off (no bendy-lib), the entity is not an animated player,
     * or no animation plays.
     */
    static boolean read(LivingEntity entity, float[] out) {
        if (!Helper.isBendEnabled() || !(entity instanceof IAnimatedPlayer player)) {
            return false;
        }
        AnimationProcessor animation = player.playerAnimator_getAnimation();
        if (animation == null || !animation.isActive()) {
            return false;
        }
        for (int i = 0; i < NAMES.length; i++) {
            Pair<Float, Float> bend = animation.getBend(NAMES[i]);
            out[i * 2] = bend == null ? 0f : bend.getLeft();
            out[i * 2 + 1] = bend == null ? 0f : bend.getRight();
        }
        return true;
    }

    @Nullable
    private static KeyframeAnimationPlayer testPose;

    /**
     * Dev clients only ({@code /bhfx pose}): holds the local player in a pose with every bend bhfx reads: right arm
     * raised forward with the elbow bent 90°, left leg forward with the knee bent 70°, torso bent 30° forward, so
     * body-part points can be checked by shots; {@code side} also turns the whole body 90° so a back camera sees it from
     * the side. {@code false} stops it. Returns a line for chat.
     */
    static String testPose(boolean on, boolean side) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return "no player";
        }
        AnimationStack layers = PlayerAnimationAccess.getPlayerAnimLayer(player);
        if (testPose != null) {
            layers.removeLayer(testPose);
            testPose = null;
        }
        if (!on) {
            return "test pose off";
        }
        KeyframeAnimation.AnimationBuilder b = new KeyframeAnimation.AnimationBuilder(AnimationFormat.JSON_EMOTECRAFT);
        b.beginTick = 0;
        b.endTick = 20;
        b.stopTick = Integer.MAX_VALUE;
        b.isLooped = true;
        b.returnTick = 0;
        b.fullyEnableParts();
        float deg = (float) (Math.PI / 180.0);
        hold(b.rightArm.pitch, -90f * deg);
        hold(b.rightArm.bend, 90f * deg);
        hold(b.leftLeg.pitch, -40f * deg);
        hold(b.leftLeg.bend, 70f * deg);
        hold(b.body.bend, 30f * deg);
        if (side) {
            hold(b.body.yaw, 90f * deg);
        }
        testPose = new KeyframeAnimationPlayer(b.build());
        layers.addAnimLayer(3000, testPose);
        return "test pose on (bends " + (Helper.isBendEnabled() ? "on" : "off: no bendy-lib") + ")";
    }

    private static void hold(KeyframeAnimation.StateCollection.State state, float value) {
        state.addKeyFrame(0, value, Ease.CONSTANT);
        state.addKeyFrame(20, value, Ease.CONSTANT);
    }
}
