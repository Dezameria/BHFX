package net.offkung.bhfx.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/** Who receives an effect started on the server. */
public final class Audience {
    public enum Kind { TRACKING, EXCEPT_CASTER, PLAYERS }

    private static final Audience TRACKING = new Audience(Kind.TRACKING, List.of());
    private static final Audience EXCEPT_CASTER = new Audience(Kind.EXCEPT_CASTER, List.of());

    private final Kind kind;
    private final List<ServerPlayer> players;

    private Audience(Kind kind, List<ServerPlayer> players) {
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

    public static Audience only(ServerPlayer player) {
        return new Audience(Kind.PLAYERS, List.of(player));
    }

    public static Audience players(Collection<ServerPlayer> players) {
        return new Audience(Kind.PLAYERS, List.copyOf(players));
    }

    public Kind kind() {
        return kind;
    }

    public List<ServerPlayer> players() {
        return players;
    }
}
