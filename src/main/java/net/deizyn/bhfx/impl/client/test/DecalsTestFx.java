package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.FxParam;
import net.deizyn.bhfx.api.client.BHFxClient;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxTexture;
import net.deizyn.bhfx.api.client.PassList;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_decals), checks {@link PassList#decal}:
 * <ul>
 *   <li>a big glowing circle turning on the ground at the target (grayscale texture as a mask, tinted, glow), with a
 *       smaller one turning the other way inside it;</li>
 *   <li>a dark scorch that covers the ground like paint (noise as a mask, black, no glow);</li>
 *   <li>a flipbook burst stepping through its 16 cells;</li>
 *   <li>the circle again on a wall facing south, 4 blocks north of the target, if a wall is there;</li>
 *   <li>a light over the big circle, to see marks and lights together.</li>
 * </ul>
 */
public final class DecalsTestFx extends Fx {
    private static final FxTexture CIRCLE = FxTexture.of(BHFxMod.id("textures/fx/magic_circle.png")).clamp();
    private static final FxTexture NOISE = FxTexture.of(BHFxMod.id("textures/fx/noise_cells.png")).clamp();
    private static final FxTexture BOOK = FxTexture.of(BHFxMod.id("textures/fx/burst_flipbook.png")).clamp();

    private final float radius;

    public DecalsTestFx(FxInit init) {
        super(init);
        this.radius = params().get(FxParam.RADIUS, 3f);
        BHFxClient.preload(CIRCLE);
        BHFxClient.preload(NOISE);
        BHFxClient.preload(BOOK);
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
        float grow = Math.min(1f, t / 0.8f);
        grow = 1f - (1f - grow) * (1f - grow);
        out.decal(NOISE, c.add(radius * 1.6, 0, radius * 0.6), radius * 0.9f).mask().color(0x000000, 0.75f * fade);
        out.decal(CIRCLE, c, radius * grow).mask().color(0x40C8FF, fade).glow(1.6f).rotation(t * 25f);
        out.decal(CIRCLE, c, radius * 0.45f * grow).mask().color(0xFFD060, fade).glow(2.2f).rotation(-t * 60f);
        out.decal(BOOK, c.add(-radius * 1.5, 0, radius * 0.8), radius * 0.6f).mask().color(0xFF7030, fade).glow(1.5f)
                .frame(4, 4, (int) (t * 10f));
        out.decal(CIRCLE, c.add(0, 2.0, -4.0), 1.6f).mask().color(0xFF40A0, fade).glow(1.8f).rotation(t * 40f)
                .facing(new Vec3(0, 0, 1)).depth(0.6f);
        out.light(c.add(0, 1.0, 0), 0x40C8FF, 0.8f * fade, radius * 2f);
    }
}
