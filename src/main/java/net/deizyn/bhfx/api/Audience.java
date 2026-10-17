package net.deizyn.bhfx.api;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Who receives an effect started on the server. */
public final class Audience {
    @ApiStatus.Internal
    public enum Kind { TRACKING, EXCEPT_CASTER, PLAYERS }

    private static final Audience TRACKING = new Audience(Kind.TRACKING, Set.of());
    private static final Audience EXCEPT_CASTER = new Audience(Kind.EXCEPT_CASTER, Set.of());

    private final Kind kind;
    private final Set<UUID> players;

    private Audience(Kind kind, Set<UUID> players) {
        this.kind = kind;
        this.players = players;
    }

    /** Every player in the effect's dimension within its range (default). */
    public static Audience tracking() {
        return TRACKING;
    }

    /** Like {@link #tracking()} but skips the caster — use when the caster's client already played it locally. */
    public static Audience exceptCaster() {
        return EXCEPT_CASTER;
    }

    /** Only this player, wherever they are in the effect's dimension (range is ignored). */
    public static Audience only(ServerPlayer player) {
        return new Audience(Kind.PLAYERS, Set.of(player.getUUID()));
    }

    /** Only these players, wherever they are in the effect's dimension (range is ignored). */
    public static Audience players(Collection<ServerPlayer> players) {
        return new Audience(Kind.PLAYERS, players.stream().map(ServerPlayer::getUUID).collect(Collectors.toUnmodifiableSet()));
    }

    @ApiStatus.Internal
    public Kind kind() {
        return kind;
    }

    /** Ids, not player objects: a respawn replaces the {@code ServerPlayer}. */
    @ApiStatus.Internal
    public Set<UUID> playerIds() {
        return players;
    }
}
