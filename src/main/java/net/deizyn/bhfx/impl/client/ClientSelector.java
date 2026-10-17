package net.deizyn.bhfx.impl.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Entity selectors for client commands. Vanilla selectors need the server (Forge's client source throws), so /bhfx test
 * evaluates a subset itself against the entities this client can see: {@code @s @p @a @r @e}, player names, UUIDs, and
 * the options {@code type, name, distance, limit, sort}. Tags, teams and scores live on the server only: use /bhfx_play fx.
 */
final class ClientSelector {
    private static final DynamicCommandExceptionType BAD = new DynamicCommandExceptionType(
            msg -> Component.literal("bhfx: " + msg));

    private ClientSelector() {
    }

    static List<Entity> select(String raw, ClientLevel level, Player self, Vec3 origin) throws CommandSyntaxException {
        if (!raw.startsWith("@")) {
            return byName(raw, level);
        }
        if (raw.length() < 2) {
            throw BAD.create("incomplete selector " + raw);
        }
        char kind = raw.charAt(1);
        String options = "";
        if (raw.length() > 2) {
            if (raw.charAt(2) != '[' || !raw.endsWith("]")) {
                throw BAD.create("bad selector " + raw);
            }
            options = raw.substring(3, raw.length() - 1);
        }

        List<Entity> candidates = new ArrayList<>();
        String sort = "arbitrary";
        int limit = Integer.MAX_VALUE;
        switch (kind) {
            case 's' -> candidates.add(self);
            case 'p' -> { candidates.addAll(level.players()); sort = "nearest"; limit = 1; }
            case 'r' -> { candidates.addAll(level.players()); sort = "random"; limit = 1; }
            case 'a' -> candidates.addAll(level.players());
            case 'e' -> level.entitiesForRendering().forEach(candidates::add);
            default -> throw BAD.create("unknown selector @" + kind);
        }

        Predicate<Entity> filter = Entity::isAlive;
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
                    Optional<EntityType<?>> type = EntityType.byString(v.contains(":") ? v : "minecraft:" + v);
                    if (type.isEmpty()) {
                        throw BAD.create("unknown entity type " + v);
                    }
                    Predicate<Entity> is = e -> e.getType() == type.get();
                    filter = filter.and(not ? is.negate() : is);
                }
                case "name" -> {
                    Predicate<Entity> is = e -> e.getName().getString().equals(v);
                    filter = filter.and(not ? is.negate() : is);
                }
                case "distance" -> {
                    double[] range = range(v);
                    filter = filter.and(e -> {
                        double d = e.position().distanceTo(origin);
                        return d >= range[0] && d <= range[1];
                    });
                }
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

        List<Entity> found = new ArrayList<>(candidates.stream().filter(filter).toList());
        Comparator<Entity> byDistance = Comparator.comparingDouble(e -> e.position().distanceToSqr(origin));
        switch (sort) {
            case "nearest" -> found.sort(byDistance);
            case "furthest" -> found.sort(byDistance.reversed());
            case "random" -> Collections.shuffle(found, new Random());
            default -> {
            }
        }
        if (found.size() > limit) {
            found = found.subList(0, limit);
        }
        if (found.isEmpty()) {
            throw BAD.create("no entity matched " + raw);
        }
        return found;
    }

    private static List<Entity> byName(String raw, ClientLevel level) throws CommandSyntaxException {
        for (Player player : level.players()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(raw)) {
                return List.of(player);
            }
        }
        try {
            UUID id = UUID.fromString(raw);
            for (Entity e : level.entitiesForRendering()) {
                if (e.getUUID().equals(id)) {
                    return List.of(e);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // not a UUID
        }
        throw BAD.create("no player named " + raw + " near you");
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
