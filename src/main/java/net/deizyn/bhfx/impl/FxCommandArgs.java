package net.deizyn.bhfx.impl;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.deizyn.bhfx.api.FxParam;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.FxSpawn;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code {...}} of /bhfx test and /bhfx_play fx, as SNBT. Three names are options of the command itself and never
 * reach the effect: {@code hold:true} (play until stopped), {@code seconds:N} and {@code range:N} (broadcast radius,
 * /bhfx_play only). Everything else becomes an effect param: {@code true}/{@code false} → bool, whole numbers → int,
 * decimals → float (an int also fills a float key), {@code "#RRGGBB"} → colour.
 */
public record FxCommandArgs(FxParams params, int durationTicks, @Nullable Double range) {
    public static final FxCommandArgs NONE = new FxCommandArgs(FxParams.EMPTY, FxSpawn.DEFAULT_DURATION, null);

    private static final int MAX_NAME = 64;
    private static final DynamicCommandExceptionType BAD = new DynamicCommandExceptionType(
            msg -> Component.literal("bhfx: " + msg));

    public static FxCommandArgs from(@Nullable CompoundTag tag) throws CommandSyntaxException {
        if (tag == null || tag.isEmpty()) {
            return NONE;
        }
        FxParams.Builder params = FxParams.builder();
        boolean hold = false;
        Float seconds = null;
        Double range = null;
        int count = 0;
        for (String name : tag.getAllKeys()) {
            Tag value = tag.get(name);
            switch (name) {
                case "hold" -> hold = bool(name, value);
                case "seconds" -> {
                    float s = number(name, value);
                    if (s <= 0f || s > 3600f) {
                        throw BAD.create("seconds must be above 0 and at most 3600 (use hold:true to play until stopped)");
                    }
                    seconds = s;
                }
                case "range" -> {
                    float r = number(name, value);
                    if (r < 1f || r > 512f) {
                        throw BAD.create("range must be between 1 and 512 blocks");
                    }
                    range = (double) r;
                }
                default -> {
                    if (name.isEmpty() || name.length() > MAX_NAME) {
                        throw BAD.create("param names must be 1-" + MAX_NAME + " characters");
                    }
                    if (++count > 64) {
                        throw BAD.create("at most 64 params");
                    }
                    put(params, name, value);
                }
            }
        }
        if (hold && seconds != null) {
            throw BAD.create("use hold or seconds, not both");
        }
        int duration = hold ? FxSpawn.UNTIL_STOPPED
                : seconds != null ? Math.max(1, Math.round(seconds * 20f)) : FxSpawn.DEFAULT_DURATION;
        return new FxCommandArgs(params.build(), duration, range);
    }

    public FxSpawn apply(FxSpawn spawn) {
        spawn.params(params).duration(durationTicks);
        if (range != null) {
            spawn.range(range);
        }
        return spawn;
    }

    private static void put(FxParams.Builder params, String name, Tag value) throws CommandSyntaxException {
        if (value instanceof ByteTag b) {
            // SNBT stores true/false as 1b/0b
            if (b.getAsByte() == 0 || b.getAsByte() == 1) {
                params.put(FxParam.b(name), b.getAsByte() == 1);
            } else {
                params.put(FxParam.i(name), (int) b.getAsByte());
            }
        } else if (value instanceof ShortTag || value instanceof IntTag) {
            params.put(FxParam.i(name), ((NumericTag) value).getAsInt());
        } else if (value instanceof LongTag l) {
            if (l.getAsLong() != (int) l.getAsLong()) {
                throw BAD.create(name + " is too large");
            }
            params.put(FxParam.i(name), (int) l.getAsLong());
        } else if (value instanceof FloatTag || value instanceof DoubleTag) {
            params.put(FxParam.f(name), ((NumericTag) value).getAsFloat());
        } else if (value instanceof StringTag s) {
            params.put(FxParam.color(name), color(name, s.getAsString()));
        } else {
            throw BAD.create(name + ": only numbers, true/false and colours (\"#FF40C0\") are supported");
        }
    }

    private static int color(String name, String text) throws CommandSyntaxException {
        String hex = text.startsWith("#") ? text.substring(1) : text.startsWith("0x") ? text.substring(2) : null;
        if (hex != null && hex.length() == 6) {
            try {
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException ignored) {
                // falls through to the error
            }
        }
        throw BAD.create(name + ": text values must be colours like \"#FF40C0\", got \"" + text + "\"");
    }

    private static boolean bool(String name, @Nullable Tag value) throws CommandSyntaxException {
        if (value instanceof ByteTag b && (b.getAsByte() == 0 || b.getAsByte() == 1)) {
            return b.getAsByte() == 1;
        }
        throw BAD.create(name + " must be true or false");
    }

    private static float number(String name, @Nullable Tag value) throws CommandSyntaxException {
        if (value instanceof NumericTag n) {
            return n.getAsFloat();
        }
        throw BAD.create(name + " must be a number");
    }
}
