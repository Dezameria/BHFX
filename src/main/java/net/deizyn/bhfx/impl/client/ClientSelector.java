package net.deizyn.bhfx.impl.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.multiplayer.ClientLevel;
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
 * the options {@code type, name, distance, limit, sort} (parsed by {@link SelectorSpec}). Tags, teams and scores live on
 * the server only: use /bhfx_play fx.
 */
final class ClientSelector {
    private ClientSelector() {
    }

    static List<Entity> select(String raw, ClientLevel level, Player self, Vec3 origin) throws CommandSyntaxException {
        SelectorSpec spec = SelectorSpec.parse(raw);
        if (spec.kind() == 0) {
            return byName(raw, level);
        }

        List<Entity> candidates = new ArrayList<>();
        switch (spec.kind()) {
            case 's' -> candidates.add(self);
            case 'p', 'r', 'a' -> candidates.addAll(level.players());
            default -> level.entitiesForRendering().forEach(candidates::add);
        }

        Predicate<Entity> filter = Entity::isAlive;
        if (spec.type() != null) {
            Optional<EntityType<?>> type = EntityType.byString(spec.type());
            if (type.isEmpty()) {
                throw SelectorSpec.BAD.create("unknown entity type " + spec.type());
            }
            Predicate<Entity> is = e -> e.getType() == type.get();
            filter = filter.and(spec.notType() ? is.negate() : is);
        }
        if (spec.name() != null) {
            Predicate<Entity> is = e -> e.getName().getString().equals(spec.name());
            filter = filter.and(spec.notName() ? is.negate() : is);
        }
        if (spec.distance() != null) {
            double[] range = spec.distance();
            filter = filter.and(e -> {
                double d = e.position().distanceTo(origin);
                return d >= range[0] && d <= range[1];
            });
        }

        List<Entity> found = new ArrayList<>(candidates.stream().filter(filter).toList());
        Comparator<Entity> byDistance = Comparator.comparingDouble(e -> e.position().distanceToSqr(origin));
        switch (spec.sort()) {
            case "nearest" -> found.sort(byDistance);
            case "furthest" -> found.sort(byDistance.reversed());
            case "random" -> Collections.shuffle(found, new Random());
            default -> {
            }
        }
        if (found.size() > spec.limit()) {
            found = found.subList(0, spec.limit());
        }
        if (found.isEmpty()) {
            throw SelectorSpec.BAD.create("no entity matched " + raw);
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
        throw SelectorSpec.BAD.create("no player named " + raw + " near you");
    }
}
