package net.deizyn.bhfx.impl;

import net.deizyn.bhfx.api.client.CameraParams;
import net.minecraftforge.common.ForgeConfigSpec;

public final class BHFxConfig {
    public enum Quality {
        OFF(0.0, false), LOW(0.35, false), MEDIUM(0.65, true), HIGH(1.0, true);

        /** Share of the pixel cap the effect passes may use. */
        public final double pixelShare;
        public final boolean bloom;

        Quality(double pixelShare, boolean bloom) {
            this.pixelShare = pixelShare;
            this.bloom = bloom;
        }

        public Quality lower() {
            return this == HIGH ? MEDIUM : LOW;
        }

        public Quality min(Quality other) {
            return ordinal() <= other.ordinal() ? this : other;
        }
    }

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.BooleanValue SERVER_ENABLED;
    public static final ForgeConfigSpec.DoubleValue SERVER_MAX_RANGE;
    public static final ForgeConfigSpec.IntValue MAX_STARTS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_PER_CASTER;

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.EnumValue<Quality> QUALITY;
    public static final ForgeConfigSpec.DoubleValue MAX_FX_MEGAPIXELS;
    public static final ForgeConfigSpec.DoubleValue GPU_BUDGET_MS;
    public static final ForgeConfigSpec.BooleanValue AUTO_DOWNGRADE;
    public static final ForgeConfigSpec.BooleanValue BLOOM;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_FX;
    public static final ForgeConfigSpec.IntValue MAX_PASSES_PER_FRAME;
    public static final ForgeConfigSpec.DoubleValue CULL_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FLASH_SCALE;
    public static final ForgeConfigSpec.DoubleValue SHAKE_SCALE;
    public static final ForgeConfigSpec.BooleanValue IMPACT_FRAMES;
    public static final ForgeConfigSpec.BooleanValue OFF_WITH_SHADERPACK;
    public static final ForgeConfigSpec.BooleanValue WARMUP;
    public static final ForgeConfigSpec.EnumValue<CameraParams.Level> CAMERA_CONTROL;
    public static final ForgeConfigSpec.BooleanValue CAMERA_FROM_OTHERS;
    public static final ForgeConfigSpec.DoubleValue FOV_SCALE;

    static {
        ForgeConfigSpec.Builder s = new ForgeConfigSpec.Builder();
        s.comment("Server side. Clients must have bhfx too; this file only controls whether the server sends effects.").push("server");
        SERVER_ENABLED = s.comment("false = never send bhfx effects. Mods that use bhfx fall back to their own visuals (e.g. Effekseer).")
                .define("enabled", true);
        SERVER_MAX_RANGE = s.comment("Upper limit for an effect's broadcast range, in blocks.")
                .defineInRange("maxRange", 256.0, 16.0, 1024.0);
        MAX_STARTS_PER_TICK = s.comment("Most effect starts per dimension per tick; extra starts are dropped (and logged).")
                .defineInRange("maxStartsPerTick", 64, 1, 1024);
        MAX_ACTIVE_PER_CASTER = s.comment("Most running effects one caster may own; extra starts are dropped.")
                .defineInRange("maxActivePerCaster", 16, 1, 256);
        s.pop();
        SERVER_SPEC = s.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("quality");
        QUALITY = c.comment("OFF = no effects and no GPU work at all (packets are dropped). LOW (no bloom) / MEDIUM / HIGH scale the effect resolution.")
                .defineEnum("quality", Quality.MEDIUM);
        MAX_FX_MEGAPIXELS = c.comment("Resolution cap for effect passes, in megapixels. Larger screens render effects below native resolution.")
                .defineInRange("maxFxMegapixels", 1.6, 0.3, 16.0);
        GPU_BUDGET_MS = c.comment("GPU time effects may use per frame. Effect resolution drops (down to a quarter of the pixels) to stay under it.")
                .defineInRange("gpuBudgetMs", 4.0, 1.0, 33.0);
        AUTO_DOWNGRADE = c.comment("If effects stay over budget for 5 s even at the lowest resolution, lower the quality for this session.")
                .define("autoDowngrade", true);
        BLOOM = c.comment("Glow around bright spell light.")
                .define("bloom", true);
        WARMUP = c.comment("Compile effect shaders while the world loads (one per frame) instead of on the first cast.")
                .define("warmup", true);
        c.pop();
        c.push("camera");
        CAMERA_CONTROL = c.comment("How much your own spells' effects may move your camera.",
                        "NONE = nothing (shake still follows comfort.shakeScale). SUBTLE = brief FOV punches.")
                .defineEnum("control", CameraParams.Level.SUBTLE);
        CAMERA_FROM_OTHERS = c.comment("Let effects cast by other players (and by mobs) use the same camera control on your screen.",
                        "Off by default so nobody else's spell can turn your camera in a fight.")
                .define("fromOthers", false);
        FOV_SCALE = c.comment("Multiplier for FOV punches. 0 disables them.")
                .defineInRange("fovScale", 1.0, 0.0, 1.0);
        c.pop();
        c.push("compat");
        OFF_WITH_SHADERPACK = c.comment("Turn post effects off while an Iris/Oculus shader pack is active (camera shake still works).",
                        "Shader packs draw the world into their own buffers, so bhfx's depth would not match what is on screen.")
                .define("offWithShaderpack", true);
        c.pop();
        c.push("limits");
        MAX_ACTIVE_FX = c.comment("Most effects alive at once; the oldest is removed beyond this.")
                .defineInRange("maxActiveFx", 32, 1, 256);
        MAX_PASSES_PER_FRAME = c.comment("Most shader passes per frame; the farthest / last-ordered passes are skipped beyond this.")
                .defineInRange("maxPassesPerFrame", 8, 1, 32);
        CULL_DISTANCE = c.comment("Effects farther than this from the camera keep ticking but draw nothing.")
                .defineInRange("cullDistance", 256.0, 16.0, 2048.0);
        c.pop();
        c.push("comfort");
        FLASH_SCALE = c.comment("Multiplier for screen flashes. 0 disables flashes (photosensitivity).")
                .defineInRange("flashScale", 1.0, 0.0, 1.0);
        SHAKE_SCALE = c.comment("Multiplier for camera shake. 0 disables it.")
                .defineInRange("shakeScale", 1.0, 0.0, 2.0);
        IMPACT_FRAMES = c.comment("Anime-style impact frames (full-screen ink / negative flashes). Turn off if they are uncomfortable.")
                .define("impactFrames", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private BHFxConfig() {
    }
}
