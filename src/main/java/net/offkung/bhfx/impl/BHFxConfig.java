package net.offkung.bhfx.impl;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BHFxConfig {
    public enum Quality {
        OFF(0.0), LOW(0.35), MEDIUM(0.65), HIGH(1.0);

        /** Share of the pixel cap the effect passes may use. */
        public final double pixelShare;

        Quality(double pixelShare) {
            this.pixelShare = pixelShare;
        }
    }

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.BooleanValue SERVER_ENABLED;
    public static final ForgeConfigSpec.DoubleValue SERVER_MAX_RANGE;

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.EnumValue<Quality> QUALITY;
    public static final ForgeConfigSpec.DoubleValue MAX_FX_MEGAPIXELS;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_FX;
    public static final ForgeConfigSpec.IntValue MAX_PASSES_PER_FRAME;
    public static final ForgeConfigSpec.DoubleValue CULL_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FLASH_SCALE;
    public static final ForgeConfigSpec.DoubleValue SHAKE_SCALE;

    static {
        ForgeConfigSpec.Builder s = new ForgeConfigSpec.Builder();
        s.comment("Server side. Clients must have bhfx too; this file only controls whether the server sends effects.").push("server");
        SERVER_ENABLED = s.comment("false = never send bhfx effects. Mods that use bhfx fall back to their own visuals (e.g. Effekseer).")
                .define("enabled", true);
        SERVER_MAX_RANGE = s.comment("Upper limit for an effect's broadcast range, in blocks.")
                .defineInRange("maxRange", 256.0, 16.0, 1024.0);
        s.pop();
        SERVER_SPEC = s.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("quality");
        QUALITY = c.comment("OFF = no effects and no GPU work at all (packets are dropped). LOW/MEDIUM/HIGH scale the effect resolution.")
                .defineEnum("quality", Quality.HIGH);
        MAX_FX_MEGAPIXELS = c.comment("Resolution cap for effect passes, in megapixels. Larger screens render effects below native resolution.")
                .defineInRange("maxFxMegapixels", 2.3, 0.3, 16.0);
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
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private BHFxConfig() {
    }
}
