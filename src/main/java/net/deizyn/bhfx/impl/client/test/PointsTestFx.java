package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.BodyPose;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxData;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;

import java.util.Map;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_points), attached to an entity: shows every named point its
 * renderer reports ({@code BHFxClient.reportPoint}, e.g. the bones of a GeckoLib model through BHSpells): a white orb
 * on each, with short red, green and blue markers along its forward, up and right. A grey orb at the entity while
 * nothing is reported. Drawn by the {@code test_data} pass.
 */
public final class PointsTestFx extends Fx {
    private static final int MAX_POINTS = 64;
    private static final int ORBS = MAX_POINTS * 4;

    private final FxData table = createData(ORBS * 2);

    public PointsTestFx(FxInit init) {
        super(init);
    }

    @Override
    protected int defaultDuration() {
        return 600;
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        Map<String, BodyPose> points = frame.points();
        int orb = 0;
        Vec3 min = null;
        Vec3 max = null;
        if (points.isEmpty()) {
            Vec3 at = frame.position();
            orb = orb(orb, frame, at, 0.15f, 0.5f, 0.5f, 0.5f);
            min = at;
            max = at;
        }
        for (BodyPose p : points.values()) {
            if (orb + 4 > ORBS) {
                break;
            }
            Vec3 at = p.position();
            orb = orb(orb, frame, at, 0.06f, 2f, 2f, 2f);
            orb = orb(orb, frame, at.add(p.forward().scale(0.2)), 0.03f, 2.5f, 0.2f, 0.2f);
            orb = orb(orb, frame, at.add(p.up().scale(0.2)), 0.03f, 0.2f, 2.5f, 0.2f);
            orb = orb(orb, frame, at.add(p.right().scale(0.2)), 0.03f, 0.2f, 0.4f, 2.5f);
            min = min == null ? at : new Vec3(Math.min(min.x, at.x), Math.min(min.y, at.y), Math.min(min.z, at.z));
            max = max == null ? at : new Vec3(Math.max(max.x, at.x), Math.max(max.y, at.y), Math.max(max.z, at.z));
        }
        Vec3 centre = min.add(max).scale(0.5);
        double radius = max.subtract(min).length() * 0.5 + 1.0;
        float fade = 1f - stopProgress(frame);
        int count = orb;
        out.add(BHFxMod.id("test_data"), PassOptions.DEFAULT.within(centre, radius), u -> u
                .data("Orbs", table)
                .f4("P0", count, fade, 0f, 0f));
    }

    private int orb(int i, FrameContext frame, Vec3 world, float radius, float r, float g, float b) {
        table.set(i * 2, frame.rel(world), radius);
        table.set(i * 2 + 1, r, g, b, 0f);
        return i + 1;
    }
}
