package net.deizyn.bhfx.impl.client.test;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxFrame;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.FxModel;
import net.deizyn.bhfx.api.client.PassList;
import net.deizyn.bhfx.api.client.PassOptions;

/**
 * Built-in effect for /bhfx test and /bhfx_play (bhfx:test_distort), checks {@link FxModel#DISTORT}:
 * <ul>
 *   <li>middle: a textured sword turning inside a slowly turning faceted orb of clear glass (bend only, a faint blue
 *       rim): the sword and the world behind bend through the orb's faces;</li>
 *   <li>left: a sword with a shell of heat haze around it (the same sword, larger, ripple only, no rim);</li>
 *   <li>right: a sword made of amber glass alone (bend, tint, rim light).</li>
 * </ul>
 * The glass is drawn at a later order than the swords, so it sees them.
 */
public final class DistortTestFx extends Fx {
    private static final FxModel SWORD = FxModel.geo(BHFxMod.id("test_model"))
            .texture(BHFxMod.id("textures/fx/test_model.png"));
    private static final FxModel ORB = FxModel.geo(BHFxMod.id("test_orb"));
    private static final PassOptions GLASS = new PassOptions(PassOptions.PARTICLES.order() + 1);

    public DistortTestFx(FxInit init) {
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
        Vec3 c = frame.position();
        // middle: a sword inside a glass orb
        Vec3 mid = c.add(0, 0.6, 0);
        out.model(SWORD, FxFrame.at(mid).turnedY(t * 1.2), 1f, FxModel.TEXTURED, u -> u.f4("MColor", 1f, 1f, 1f, fade));
        out.model(ORB, FxFrame.at(mid.add(0, 0.9, 0)).turnedY(t * 0.4).turnedX(t * 0.25), 1.3f, FxModel.DISTORT, GLASS,
                u -> u.f4("MColor", 0.9f, 0.97f, 1f, fade).f4("MParams", 0.35f, 0f, 0f, 0.5f));
        // left: heat haze round a sword
        FxFrame left = FxFrame.at(c.add(-3.2, 0.6, 0)).turnedY(-t * 0.8);
        out.model(SWORD, left, 1f, FxModel.TEXTURED, u -> u.f4("MColor", 1f, 1f, 1f, fade));
        out.model(SWORD, FxFrame.at(c.add(-3.2, 0.45, 0)).turnedY(-t * 0.8), 1.25f, FxModel.DISTORT, GLASS,
                u -> u.f4("MColor", 1f, 1f, 1f, fade).f4("MParams", 0f, 0.6f, 4f, 0f));
        // right: a sword of amber glass
        out.model(SWORD, FxFrame.at(c.add(3.2, 0.6, 0)).turnedY(t), 1.6f, FxModel.DISTORT, GLASS,
                u -> u.f4("MColor", 1f, 0.8f, 0.45f, fade).f4("MParams", 0.3f, 0.15f, 0f, 1.2f));
    }
}
