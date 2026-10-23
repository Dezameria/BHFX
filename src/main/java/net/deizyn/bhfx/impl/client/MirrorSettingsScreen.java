package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.impl.BHFxConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.IntFunction;

/**
 * bhfx Config → Mirror. The [mirror] part of bhfx-client.toml; {@code MirrorRenderer} reads it every frame, so changes
 * show at once. Saved to disk when the screen closes.
 */
final class MirrorSettingsScreen extends Screen {
    private static final int ROW = 24;

    private final Screen parent;

    MirrorSettingsScreen(Screen parent) {
        super(Component.literal("bhfx mirror settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int full = Math.min(310, width - 20);
        int left = (width - full) / 2;
        int y = 32;

        addRenderableWidget(new IntSlider(left, y, full, BHFxConfig.MIRROR_DISTANCE, 8, 128,
                v -> "Distance: " + v + " blocks",
                "Mirrors farther than this from the camera show no reflection."));
        y += ROW;
        addRenderableWidget(new IntSlider(left, y, full, BHFxConfig.MIRROR_MAX_PLANES, 0, 16,
                v -> v == 0 ? "Surfaces: off" : "Surfaces: " + v,
                "Most mirror surfaces drawn per frame, nearest first. A flat wall of mirrors counts as one;\n"
                        + "a closed mirror room needs 6. 0 turns reflections off."));
        y += ROW;
        addRenderableWidget(new IntSlider(left, y, full, BHFxConfig.MIRROR_PLAYER_RANGE, 4, 64,
                v -> "Player range: " + v + " blocks",
                "Players farther than this from a mirror are not reflected in it."));
        y += ROW;
        addRenderableWidget(new IntSlider(left, y, full, BHFxConfig.MIRROR_MAX_REFLECTIONS, 1, 64,
                v -> "Player drawings: " + v,
                "Most player drawings in mirrors per frame, over all surfaces. Each costs about as much as\n"
                        + "drawing that player again (Figura avatars more). This is the expensive part."));

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        BHFxConfig.CLIENT_SPEC.save();
        minecraft.setScreen(parent);
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final ForgeConfigSpec.IntValue config;
        private final int min;
        private final int max;
        private final IntFunction<String> label;

        IntSlider(int x, int y, int w, ForgeConfigSpec.IntValue config, int min, int max, IntFunction<String> label,
                  String tooltip) {
            super(x, y, w, 20, Component.empty(), (config.get() - min) / (double) (max - min));
            this.config = config;
            this.min = min;
            this.max = max;
            this.label = label;
            setTooltip(Tooltip.create(Component.literal(tooltip)));
            updateMessage();
        }

        private int current() {
            return (int) Math.round(min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label.apply(current())));
        }

        @Override
        protected void applyValue() {
            config.set(current());
        }
    }
}
