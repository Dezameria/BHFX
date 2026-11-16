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
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_data): 256 glowing orbs circling the target, their places
 * written into an {@link FxData} table every frame and read by one pass ({@code test_data.fsh}). The colours are
 * written once in the constructor. Positions and colours interleave, so each frame's dirty range is the whole table
 * (8 KB): this checks that rewriting a table every frame works, not that small ranges stay small (JUnit covers that).
 */
public final class DataTestFx extends Fx {
    private static final int ORBS = 256;
    private static final float TAU = (float) (Math.PI * 2.0);

    private final float radius;
    private final FxData table = createData(ORBS * 2);
    private final float[] angle = new float[ORBS];
    private final float[] ring = new float[ORBS];
    private final float[] height = new float[ORBS];
    private final float[] speed = new float[ORBS];
    private final float[] size = new float[ORBS];

    public DataTestFx(FxInit init) {
        super(init);
        this.radius = params().get(FxParam.RADIUS, 6f);
        for (int i = 0; i < ORBS; i++) {
            angle[i] = random().nextFloat() * TAU;
            ring[i] = radius * (0.3f + 0.7f * random().nextFloat());
            height[i] = 0.5f + radius * 0.8f * random().nextFloat();
            speed[i] = (0.4f + random().nextFloat()) * (random().nextBoolean() ? 1f : -1f);
            size[i] = 0.08f + 0.12f * random().nextFloat();
            float hue = (float) i / ORBS;
            table.set(i * 2 + 1, hueR(hue) * 2f, hueG(hue) * 2f, hueB(hue) * 2f, 0f);
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
        Vec3 rel = frame.rel(c);
        for (int i = 0; i < ORBS; i++) {
            float a = angle[i] + speed[i] * t;
            float bob = (float) Math.sin(t * 1.3f + i) * 0.3f;
            table.set(i * 2, (float) rel.x + (float) Math.cos(a) * ring[i], (float) rel.y + height[i] + bob,
                    (float) rel.z + (float) Math.sin(a) * ring[i], size[i]);
        }
        float fadeIn = Math.min(1f, t / 0.5f);
        float fade = fadeIn * (1f - stopProgress(frame));
        PassOptions bounds = PassOptions.DEFAULT.within(c.add(0, radius * 0.5, 0), radius * 1.6 + 2.0);
        out.add(BHFxMod.id("test_data"), bounds, u -> u
                .data("Orbs", table)
                .f4("P0", ORBS, fade, 0f, 0f));
    }

    private static float hueR(float h) {
        return channel(h, 0f);
    }

    private static float hueG(float h) {
        return channel(h, 1f / 3f);
    }

    private static float hueB(float h) {
        return channel(h, 2f / 3f);
    }

    private static float channel(float h, float offset) {
        return 0.5f + 0.5f * (float) Math.cos(TAU * (h + offset));
    }
}
