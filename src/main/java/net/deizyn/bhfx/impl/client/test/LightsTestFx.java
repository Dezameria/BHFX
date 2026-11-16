package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.FxParam;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxData;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_lights): a red, a green and a blue light circling the
 * target near the ground and a white one pulsing above it, each with a small orb where it is (the {@code test_data}
 * pass). Checks {@link PassList#light}: the ground and walls nearby take the colours, the colours mix where they
 * meet, nothing is lit past a light's radius, and many of these at once still cost one pass.
 */
public final class LightsTestFx extends Fx {
    private static final int[] COLORS = {0xFF3020, 0x30FF40, 0x3060FF, 0xFFF0D0};
    private static final float TAU = (float) (Math.PI * 2.0);

    private final float radius;
    private final FxData orbs = createData(COLORS.length * 2);

    public LightsTestFx(FxInit init) {
        super(init);
        this.radius = params().get(FxParam.RADIUS, 4f);
        for (int i = 0; i < COLORS.length; i++) {
            int c = COLORS[i];
            orbs.set(i * 2 + 1, ((c >> 16) & 255) / 255f * 2f, ((c >> 8) & 255) / 255f * 2f, (c & 255) / 255f * 2f, 0f);
        }
    }

    @Override
    protected int defaultDuration() {
        return 400;
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        float t = seconds(frame);
        Vec3 c = frame.position();
        float fade = Math.min(1f, t / 0.5f) * (1f - stopProgress(frame));
        for (int i = 0; i < COLORS.length; i++) {
            Vec3 at;
            float strength;
            float reach;
            if (i < 3) {
                float a = t * 0.8f + i * TAU / 3f;
                at = c.add(Math.cos(a) * radius, 1.2, Math.sin(a) * radius);
                strength = 1.5f;
                reach = radius * 1.6f;
            } else {
                at = c.add(0, 2.5, 0);
                strength = 0.6f + 0.6f * (float) Math.sin(t * 3f);
                reach = radius * 2.2f;
            }
            out.light(at, COLORS[i], strength * fade, reach);
            Vec3 rel = frame.rel(at);
            orbs.set(i * 2, (float) rel.x, (float) rel.y, (float) rel.z, 0.15f);
        }
        out.add(BHFxMod.id("test_data"), PassOptions.DEFAULT.within(c.add(0, 1.5, 0), radius + 3.0), u -> u
                .data("Orbs", orbs)
                .f4("P0", COLORS.length, fade, 0f, 0f));
    }
}
