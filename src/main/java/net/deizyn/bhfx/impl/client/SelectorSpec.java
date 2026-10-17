package net.deizyn.bhfx.impl.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A parsed client selector (see {@link ClientSelector}): pure text parsing, no world access, so it can be unit tested.
 *
 * @param kind     's', 'p', 'r', 'a' or 'e'; 0 for a plain player name or UUID in {@code name}
 * @param type     entity type id ("minecraft:" added when missing), or null
 * @param name     player name / UUID when {@code kind} is 0, else the name= filter, or null
 * @param distance {min, max} in blocks, or null
 * @param limit    at most this many, after sorting
 * @param sort     nearest, furthest, random or arbitrary
 */
record SelectorSpec(char kind, @Nullable String type, boolean notType, @Nullable String name, boolean notName,
                    double @Nullable [] distance, int limit, String sort) {
    static final DynamicCommandExceptionType BAD = new DynamicCommandExceptionType(msg -> Component.literal("bhfx: " + msg));

    static SelectorSpec parse(String raw) throws CommandSyntaxException {
        if (!raw.startsWith("@")) {
            return new SelectorSpec((char) 0, null, false, raw, false, null, 1, "arbitrary");
        }
        if (raw.length() < 2) {
            throw BAD.create("incomplete selector " + raw);
        }
        char kind = raw.charAt(1);
        String sort;
        int limit;
        switch (kind) {
            case 's' -> { sort = "arbitrary"; limit = 1; }
            case 'p' -> { sort = "nearest"; limit = 1; }
            case 'r' -> { sort = "random"; limit = 1; }
            case 'a', 'e' -> { sort = "arbitrary"; limit = Integer.MAX_VALUE; }
            default -> throw BAD.create("unknown selector @" + kind);
        }
        String options = "";
        if (raw.length() > 2) {
            if (raw.charAt(2) != '[' || !raw.endsWith("]")) {
                throw BAD.create("bad selector " + raw);
            }
            options = raw.substring(3, raw.length() - 1);
        }

        String type = null;
        boolean notType = false;
        String name = null;
        boolean notName = false;
        double[] distance = null;
        for (String option : options.isBlank() ? new String[0] : options.split(",")) {
            int eq = option.indexOf('=');
            if (eq <= 0) {
                throw BAD.create("bad selector option '" + option.trim() + "'");
            }
            String key = option.substring(0, eq).trim();
            String value = option.substring(eq + 1).trim();
            boolean not = value.startsWith("!");
            String v = unquote(not ? value.substring(1) : value);
            switch (key) {
                case "type" -> {
                    if (v.startsWith("#")) {
                        throw BAD.create("entity type tags (type=#...) only work in /bhfx_play fx");
                    }
                    type = v.contains(":") ? v : "minecraft:" + v;
                    notType = not;
                }
                case "name" -> {
                    name = v;
                    notName = not;
                }
                case "distance" -> distance = range(v);
                case "limit" -> {
                    try {
                        limit = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        throw BAD.create("limit must be a whole number");
                    }
                    if (limit < 1) {
                        throw BAD.create("limit must be at least 1");
                    }
                }
                case "sort" -> {
                    if (!List.of("nearest", "furthest", "random", "arbitrary").contains(v)) {
                        throw BAD.create("sort must be nearest, furthest, random or arbitrary");
                    }
                    sort = v;
                }
                case "tag", "team", "scores", "nbt", "advancements", "predicate", "gamemode", "level" ->
                        throw BAD.create(key + "= needs the server: use /bhfx_play fx (op) instead of /bhfx test");
                default -> throw BAD.create("selector option " + key + " is not supported in /bhfx test");
            }
        }
        return new SelectorSpec(kind, type, notType, name, notName, distance, limit, sort);
    }

    /** "5", "..10", "5..", "5..10" → {min, max}. */
    private static double[] range(String v) throws CommandSyntaxException {
        try {
            int dots = v.indexOf("..");
            if (dots < 0) {
                double d = Double.parseDouble(v);
                return new double[]{d, d};
            }
            String lo = v.substring(0, dots);
            String hi = v.substring(dots + 2);
            return new double[]{lo.isEmpty() ? 0.0 : Double.parseDouble(lo), hi.isEmpty() ? Double.MAX_VALUE : Double.parseDouble(hi)};
        } catch (NumberFormatException e) {
            throw BAD.create("bad distance " + v);
        }
    }

    private static String unquote(String v) {
        return v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"") ? v.substring(1, v.length() - 1) : v;
    }
}
