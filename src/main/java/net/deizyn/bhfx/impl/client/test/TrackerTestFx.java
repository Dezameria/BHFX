package net.deizyn.bhfx.impl.client.test;

import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.FxParam;
import net.deizyn.bhfx.api.client.FrameContext;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.api.client.PassList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/**
 * /bhfx_test tracker: a warning ring that stays for 60 s with a short broadcast range, plus a HUD line with its age.
 * Stand outside the range when it starts, walk in, and the ring must appear with the age already counted (not 0 s):
 * that is the tracker sending it late with startAge.
 */
public final class TrackerTestFx extends Fx {
    public static final FxParam<Float> RANGE = FxParam.f("range");
    public static final int SECONDS = 60;

    public TrackerTestFx(FxInit init) {
        super(init);
    }

    @Override
    protected int defaultDuration() {
        return SECONDS * 20;
    }

    @Override
    protected double cullDistance() {
        return 512.0;
    }

    @Override
    protected void passes(FrameContext frame, PassList out) {
        float fade = 1f - stopProgress(frame);
        Vec3 target = frame.rel(init().target());
        out.add(BHFxMod.id("test"), u -> u
                .vec3("Target", target)
                .f4("P0", 3f, 1f, 0f, -1f)
                .f4("P1", seedF(), fade, 0f, 0f));
    }

    @Override
    protected void hud(GuiGraphics graphics, FrameContext frame) {
        float range = params().get(RANGE, 16f);
        double distance = frame.cameraPos().distanceTo(init().target());
        String text = String.format("bhfx tracker test: %.1f / %d s, %.0f blocks away (range %.0f), joined at %.1f s",
                ticks(frame) / 20f, SECONDS, distance, range, init().startAge() / 20f);
        graphics.drawString(Minecraft.getInstance().font, text, 6, 6, 0xFFE0A0);
    }
}
