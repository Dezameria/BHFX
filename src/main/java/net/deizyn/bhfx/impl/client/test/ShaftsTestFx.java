package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxShafts;
import net.deizyn.bhfx.api.client.PassList;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_shafts), checks {@link FxShafts}:
 * <ul>
 *   <li>warm light pouring down onto the target from a point 10 blocks above it, in a cone that sways a little, with
 *       a light where it lands;</li>
 *   <li>six blocks to the side, thin blue rays bursting in every direction out of a point two blocks up.</li>
 * </ul>
 */
public final class ShaftsTestFx extends Fx {
    private final FxShafts down = FxShafts.cone(Vec3.ZERO, new Vec3(0, -1, 0), 12f, 22f)
            .color(0xFFD890, 1.3f).rays(11f, 0.55f);
    private final FxShafts burst = FxShafts.around(Vec3.ZERO, 4.5f)
            .color(0x60A8FF, 1.6f).rays(5f, 0.85f).mist(0.05f).drift(2.5f);

    public ShaftsTestFx(FxInit init) {
        super(init);
    }

    @Override
    protected int defaultDuration() {
        return 400;
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        float t = seconds(frame);
        float fade = Math.min(1f, t / 0.5f) * (1f - stopProgress(frame));
        Vec3 c = frame.position();
        down.move(c.add(0, 10, 0), new Vec3(Math.sin(t * 0.5) * 0.12, -1, Math.cos(t * 0.4) * 0.12));
        down.draw(frame, out, t, fade);
        out.light(c.add(0, 0.5, 0), 0xFFD890, 0.8f * fade, 5f);
        Vec3 orb = c.add(6, 2, 0);
        burst.move(orb, null);
        burst.draw(frame, out, t, fade);
        out.light(orb, 0x60A8FF, 0.9f * fade, 5f);
    }
}
