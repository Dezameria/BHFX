package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.api.client.CameraParams;
import net.deizyn.bhfx.impl.BHFxConfig;
import net.deizyn.bhfx.impl.BHFxConfig.Quality;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.DoubleFunction;

/**
 * Mods menu → bhfx → Config. Edits bhfx-client.toml live: every change applies on the next frame, so the player can
 * cast with the screen closed and come back. Saved to disk when the screen closes.
 */
public final class BHFxSettingsScreen extends Screen {
    /** One click sets quality, GPU budget and resolution cap together. MEDIUM matches the config defaults. */
    private enum Preset {
        LOW("Low", Quality.LOW, 2.0, 1.0),
        MEDIUM("Medium", Quality.MEDIUM, 4.0, 1.6),
        HIGH("High", Quality.HIGH, 6.0, 2.3),
        MAX("Max", Quality.HIGH, 10.0, 8.3);

        final String label;
        final Quality quality;
        final double budgetMs;
        final double megapixels;

        Preset(String label, Quality quality, double budgetMs, double megapixels) {
            this.label = label;
            this.quality = quality;
            this.budgetMs = budgetMs;
            this.megapixels = megapixels;
        }

        boolean matches() {
            return BHFxConfig.QUALITY.get() == quality
                    && Math.abs(BHFxConfig.GPU_BUDGET_MS.get() - budgetMs) < 1e-3
                    && Math.abs(BHFxConfig.MAX_FX_MEGAPIXELS.get() - megapixels) < 1e-3;
        }
    }

    private static final int ROW = 24;

    private final Screen parent;

    public BHFxSettingsScreen(Screen parent) {
        super(Component.literal("bhfx effect settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int full = Math.min(310, width - 20);
        int half = (full - 10) / 2;
        int left = (width - full) / 2;
        int right = left + half + 10;
        int y = 32;

        int presetW = (full - 3 * 4) / 4;
        for (int i = 0; i < Preset.values().length; i++) {
            Preset p = Preset.values()[i];
            String label = p.matches() ? "> " + p.label + " <" : p.label;
            addRenderableWidget(Button.builder(Component.literal(label), b -> applyPreset(p))
                    .bounds(left + i * (presetW + 4), y, presetW, 20)
                    .tooltip(Tooltip.create(Component.literal(String.format(
                            "Quality %s, GPU budget %.0f ms, resolution cap %.1f MP", p.quality, p.budgetMs, p.megapixels))))
                    .build());
        }
        y += ROW + 4;

        addRenderableWidget(CycleButton.<Quality>builder(q -> Component.literal(q.name()))
                .withValues(Quality.values())
                .withInitialValue(BHFxConfig.QUALITY.get())
                .withTooltip(q -> Tooltip.create(Component.literal(
                        "OFF = no effects and no GPU work. LOW (no bloom) / MEDIUM / HIGH scale the effect resolution.")))
                .create(left, y, half, 20, Component.literal("Quality"), (b, q) -> {
                    BHFxConfig.QUALITY.set(q);
                    changed();
                    rebuildWidgets(); // preset marker and resolution hint depend on quality
                }));
        addRenderableWidget(toggle(right, y, half, "Bloom", BHFxConfig.BLOOM, "Glow around bright spell light."));
        y += ROW;

        addRenderableWidget(new Slider(left, y, full, BHFxConfig.GPU_BUDGET_MS, 1.0, 16.0, 0.5,
                v -> String.format("GPU budget: %.1f ms", v),
                "GPU time effects may use per frame. Above it, effect resolution drops (down to a quarter of the pixels).\n"
                        + "Lower = smoother on weak GPUs, blurrier effects under load."));
        y += ROW;

        addRenderableWidget(new Slider(left, y, full, BHFxConfig.MAX_FX_MEGAPIXELS, 0.3, 8.3, 0.1,
                v -> String.format("Resolution cap: %.1f MP (%s)", v, effectSizeHint(v)),
                "Most pixels an effect pass may use. Larger windows draw effects below native resolution.\n"
                        + "2.3 MP is about 1080p; 8.3 MP is about 4K."));
        y += ROW;

        addRenderableWidget(toggle(left, y, half, "Auto-lower quality", BHFxConfig.AUTO_DOWNGRADE,
                "If effects stay over budget for 5 s even at the lowest resolution, lower the quality until the game restarts."));
        addRenderableWidget(toggle(right, y, half, "Off with shader pack", BHFxConfig.OFF_WITH_SHADERPACK,
                "Turn post effects off while an Iris/Oculus shader pack is active."));
        y += ROW;

        addRenderableWidget(new Slider(left, y, half, BHFxConfig.SHAKE_SCALE, 0.0, 2.0, 0.05,
                v -> String.format("Shake: %d%%", Math.round(v * 100)), "Camera shake multiplier. 0 disables it."));
        addRenderableWidget(new Slider(right, y, half, BHFxConfig.FLASH_SCALE, 0.0, 1.0, 0.05,
                v -> String.format("Flashes: %d%%", Math.round(v * 100)),
                "Screen flash multiplier. 0 disables flashes (photosensitivity)."));
        y += ROW;

        addRenderableWidget(CycleButton.<CameraParams.Level>builder(l -> Component.literal(l.name()))
                .withValues(CameraParams.Level.values())
                .withInitialValue(BHFxConfig.CAMERA_CONTROL.get())
                .withTooltip(l -> Tooltip.create(Component.literal(
                        "How much your own spells' effects may move your camera.")))
                .create(left, y, half, 20, Component.literal("Camera"), (b, l) -> {
                    BHFxConfig.CAMERA_CONTROL.set(l);
                    changed();
                }));
        addRenderableWidget(toggle(right, y, half, "Impact frames", BHFxConfig.IMPACT_FRAMES,
                "Anime-style full-screen ink / negative flashes."));

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, ClientFxManager.liveLine(), width / 2, height - 42, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        BHFxConfig.CLIENT_SPEC.save();
        minecraft.setScreen(parent);
    }

    private void applyPreset(Preset p) {
        BHFxConfig.QUALITY.set(p.quality);
        BHFxConfig.GPU_BUDGET_MS.set(p.budgetMs);
        BHFxConfig.MAX_FX_MEGAPIXELS.set(p.megapixels);
        changed();
        rebuildWidgets();
    }

    /** A setting moved: let it take effect now instead of after an earlier automatic downgrade. */
    private static void changed() {
        ClientFxManager.settingsChanged();
    }

    /** Effect pass size at this cap for the current window and quality, as the renderer will pick it. */
    private String effectSizeHint(double megapixels) {
        int w = minecraft.getWindow().getWidth();
        int h = minecraft.getWindow().getHeight();
        double scale = Math.min(1.0, Math.sqrt(megapixels * 1.0e6 * BHFxConfig.QUALITY.get().pixelShare / ((double) w * h)));
        return scale >= 1.0 ? "native " + w + "x" + h : Math.round(w * scale) + "x" + Math.round(h * scale);
    }

    private static CycleButton<Boolean> toggle(int x, int y, int w, String label, ForgeConfigSpec.BooleanValue value,
                                               String tooltip) {
        return CycleButton.onOffBuilder(value.get())
                .withTooltip(v -> Tooltip.create(Component.literal(tooltip)))
                .create(x, y, w, 20, Component.literal(label), (b, v) -> {
                    value.set(v);
                    changed();
                });
    }

    private static final class Slider extends AbstractSliderButton {
        private final ForgeConfigSpec.DoubleValue config;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleFunction<String> label;

        Slider(int x, int y, int w, ForgeConfigSpec.DoubleValue config, double min, double max, double step,
               DoubleFunction<String> label, String tooltip) {
            super(x, y, w, 20, Component.empty(), (config.get() - min) / (max - min));
            this.config = config;
            this.min = min;
            this.max = max;
            this.step = step;
            this.label = label;
            setTooltip(Tooltip.create(Component.literal(tooltip)));
            updateMessage();
        }

        private double current() {
            double v = min + value * (max - min);
            return Math.max(min, Math.min(max, Math.round(v / step) * step));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label.apply(current())));
        }

        @Override
        protected void applyValue() {
            config.set(current());
            changed();
        }
    }
}
