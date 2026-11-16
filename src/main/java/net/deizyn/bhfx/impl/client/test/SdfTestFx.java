package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.BodyPart;
import net.deizyn.bhfx.api.client.BodyPose;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxData;
import net.deizyn.bhfx.api.client.FxFrame;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;

import java.util.Optional;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_sdf): a hand of light drawn from signed distances
 * ({@code test_sdf.fsh}). Its bones are worked out here every frame with {@link FxFrame} (forward kinematics: each
 * joint turned from the last) and written into an {@link FxData} table. Attached to an entity it sits over the right
 * hand ({@link BodyPart#RIGHT_HAND}) and follows the arm; otherwise it stands at the target and turns. It opens and
 * closes every few seconds.
 */
public final class SdfTestFx extends Fx {
    private static final int BONES = 15;
    /** Knuckles across the palm (index to little, + to the thumb side), each finger's length, splay, radius. */
    private static final double[] KNUCKLE_X = {0.27, 0.09, -0.09, -0.27};
    private static final double[] LENGTH = {0.78, 0.86, 0.80, 0.62};
    private static final double[] SPLAY = {0.12, 0.03, -0.05, -0.15};
    private static final double[] RADIUS = {0.085, 0.09, 0.085, 0.075};
    private static final double[] SHARE = {0.45, 0.30, 0.25};
    private static final double[] CURL = {1.35, 1.65, 1.1};
    private static final double[] THUMB_LEN = {0.32, 0.28, 0.22};
    private static final double[] THUMB_CURL = {0.4, 0.7, 0.7};

    private final FxData table = createData(3 + BONES * 2);

    public SdfTestFx(FxInit init) {
        super(init);
    }

    @Override
    protected int defaultDuration() {
        return 400;
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        float t = seconds(frame);
        Optional<BodyPose> held = init().followId() >= 0 ? frame.part(BodyPart.RIGHT_HAND) : Optional.empty();
        double s;
        FxFrame hand;
        if (held.isPresent()) {
            BodyPose p = held.get();
            // fingers away from the arm (its up runs back to the shoulder), back of the hand outwards, thumb forwards
            s = 0.5;
            hand = new FxFrame(p.position(), p.forward(), p.right(), p.up().scale(-1)).moved(0, 0, -0.1 * s);
        } else {
            s = 1.2;
            double a = t * 0.6;
            hand = FxFrame.looking(frame.position().add(0, 0.4, 0), new Vec3(0, 1, 0), new Vec3(Math.cos(a), 0, Math.sin(a)));
        }
        Vec3 centre = hand.point(0, 0, 0.9 * s);
        hand = hand.rel(frame);
        table.setFrame(0, hand);

        double curl = 0.5 - 0.5 * Math.cos(t * 1.8);
        int bone = 0;
        for (int f = 0; f < 4; f++) {
            FxFrame j = hand.moved(KNUCKLE_X[f] * s, 0, 0.86 * s).turnedY(SPLAY[f] * (1.0 - 0.6 * curl));
            double r = RADIUS[f] * s;
            for (int k = 0; k < 3; k++) {
                j = j.turnedX(CURL[k] * curl);
                double len = LENGTH[f] * SHARE[k] * s;
                double r1 = r * (k == 2 ? 0.78 : 0.92);
                bone = writeBone(bone, j.origin(), r, j.point(0, 0, len), r1);
                j = j.moved(0, 0, len);
                r = r1;
            }
        }
        FxFrame th = hand.moved(0.3 * s, -0.06 * s, 0.22 * s).turnedY(0.9 - 0.5 * curl).turnedZ(-0.6);
        double r = 0.1 * s;
        for (int k = 0; k < 3; k++) {
            th = th.turnedX(THUMB_CURL[k] * curl);
            double len = THUMB_LEN[k] * s;
            double r1 = r * 0.9;
            bone = writeBone(bone, th.origin(), r, th.point(0, 0, len), r1);
            th = th.moved(0, 0, len);
            r = r1;
        }

        float fade = Math.min(1f, t / 0.5f) * (1f - stopProgress(frame));
        Vec3 c = frame.rel(centre);
        float radius = (float) (1.35 * s);
        PassOptions bounds = PassOptions.DEFAULT.within(centre, radius + 0.5 * s);
        int bones = bone;
        out.add(BHFxMod.id("test_sdf"), bounds, u -> u
                .data("Hand", table)
                .f4("P0", bones, (float) s, fade, 0f)
                .f4("Bound", (float) c.x, (float) c.y, (float) c.z, radius)
                .f3("Tint", 0.35f, 0.75f, 1.0f));
    }

    private int writeBone(int i, Vec3 a, double ra, Vec3 b, double rb) {
        table.set(3 + i * 2, a, (float) ra);
        table.set(4 + i * 2, b, (float) rb);
        return i + 1;
    }
}
