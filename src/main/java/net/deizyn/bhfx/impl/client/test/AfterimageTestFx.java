package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxAfterimage;
import net.deizyn.bhfx.api.client.FxFrame;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxModel;
import net.deizyn.bhfx.api.client.FxPose;
import net.deizyn.bhfx.api.client.PassList;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_afterimage), checks {@link FxAfterimage}: a sword flies
 * round the target in a fast circle, its blade bending back and forth ({@link FxPose}), and leaves eight glowing
 * ghosts behind it, each where and how the sword was a moment earlier (so the ghosts bend differently), dimmer the
 * older. For two seconds in every six it stops: the ghosts fade and none pile up on the resting sword.
 */
public final class AfterimageTestFx extends Fx {
    private static final FxModel SWORD = FxModel.geo(BHFxMod.id("test_model"))
            .texture(BHFxMod.id("textures/fx/test_model.png"));

    private final FxAfterimage trail = new FxAfterimage(8, 0.045f);
    private final FxPose pose = new FxPose();

    public AfterimageTestFx(FxInit init) {
        super(init);
    }

    @Override
    protected int defaultDuration() {
        return 400;
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        float t = seconds(frame);
        float fade = Math.min(1f, t / 0.3f) * (1f - stopProgress(frame));
        // moving for four seconds, still for two
        float cycle = t % 6f;
        float moving = Math.min(cycle, 4f) + (float) Math.floor(t / 6f) * 4f;
        double a = moving * 4.5;
        Vec3 c = frame.position().add(0, 1.6, 0);
        Vec3 at = c.add(Math.cos(a) * 2.6, Math.sin(a * 0.5) * 0.6, Math.sin(a) * 2.6);
        FxFrame sword = FxFrame.at(at).turnedY(-a).turnedX(0.5);
        pose.rotation("blade", 0f, 0f, 40f * (float) Math.sin(moving * 9.0));
        trail.record(t, sword, 1.4f, pose);
        trail.drawGlow(out, SWORD, t, 0x66C8FF, 1.3f * fade);
        out.model(SWORD, sword, 1.4f, pose, FxModel.TEXTURED, u -> u.f4("MColor", 1f, 1f, 1f, fade));
    }
}
