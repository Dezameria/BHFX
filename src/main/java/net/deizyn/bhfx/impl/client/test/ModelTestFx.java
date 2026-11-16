package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.BodyPart;
import net.deizyn.bhfx.api.client.BodyPose;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxFrame;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxModel;
import net.deizyn.bhfx.api.client.FxPose;
import net.deizyn.bhfx.api.client.PassList;

import java.util.Optional;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_model): bhfx's own test sword ({@code geo/test_model.geo.json},
 * box UVs, an inflated cube, a rotated cube, a rotated bone, per-face UVs) drawn by bhfx with three materials, side by
 * side at the target, turning: {@link FxModel#TEXTURED} (left), {@link FxModel#GLOW} (middle, cyan, pulsing) and
 * {@link FxModel#DISSOLVE} (right, burning away and back every 4 s), and a fourth one further right posed by an
 * {@link FxPose} (textured): the blade swings and stretches, the gem spins, bobs and hides for a moment every 3 s.
 * Attached to an entity, one glowing sword is held in its right hand instead ({@link BodyPart#RIGHT_HAND}).
 */
public final class ModelTestFx extends Fx {
    private static final FxModel SWORD = FxModel.geo(BHFxMod.id("test_model"))
            .texture(BHFxMod.id("textures/fx/test_model.png"));

    private final FxPose pose = new FxPose();

    public ModelTestFx(FxInit init) {
        super(init);
    }

    @Override
    protected int defaultDuration() {
        return 400;
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        float t = seconds(frame);
        float fade = Math.min(1f, t / 0.4f) * (1f - stopProgress(frame));
        Optional<BodyPose> hand = init().followId() >= 0 ? frame.part(BodyPart.RIGHT_HAND) : Optional.empty();
        if (hand.isPresent()) {
            BodyPose p = hand.get();
            // the grip in the hand, the blade along the arm's forward
            FxFrame held = new FxFrame(p.position(), p.right(), p.forward(), p.up().scale(-1)).moved(0, -0.1, 0);
            out.model(SWORD, held, 1f, FxModel.GLOW, u -> u.f4("MColor", 0.4f * fade, 0.9f * fade, 1.6f * fade, 1f)
                    .f4("MParams", 1f, 0f, 0f, 0f));
            return;
        }
        Vec3 base = frame.position().add(0, 0.2, 0);
        float turn = t * 0.8f;
        out.model(SWORD, FxFrame.at(base.add(-1.6, 0, 0)).turnedY(turn), 1f, FxModel.TEXTURED,
                u -> u.f4("MColor", 1f, 1f, 1f, fade));
        out.model(SWORD, FxFrame.at(base).turnedY(turn), 1f, FxModel.GLOW,
                u -> u.f4("MColor", 0.4f * fade, 0.9f * fade, 1.6f * fade, 1f).f4("MParams", 1f, 0f, 0f, 0f));
        float cut = 0.5f - 0.5f * (float) Math.cos(t * Math.PI / 2.0);
        out.model(SWORD, FxFrame.at(base.add(1.6, 0, 0)).turnedY(turn), 1f, FxModel.DISSOLVE,
                u -> u.f4("MColor", 3f, 1.2f, 0.3f, 1f).f4("MParams", cut, 0.08f, 6f, 0f));
        float swing = (float) Math.sin(t * 2.0);
        pose.rotation("blade", 0f, 0f, 35f * swing)
                .scale("blade", 1f, 1f + 0.3f * (0.5f + 0.5f * (float) Math.cos(t * 3.0)), 1f)
                .rotation("gem", 0f, t * 180f, 0f)
                .position("gem", 0f, 1.5f * (float) Math.sin(t * 4.0), 0f)
                .scale("gem", (t % 3f) > 2.5f ? 0f : 1f); // 0 = hidden
        out.model(SWORD, FxFrame.at(base.add(3.2, 0, 0)).turnedY(turn), 1f, pose, FxModel.TEXTURED,
                u -> u.f4("MColor", 1f, 1f, 1f, fade));
    }
}
