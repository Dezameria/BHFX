package net.deizyn.bhfx.api;

import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Names "the effect in this slot for this owner", so code that starts an effect and code that stops it (e.g. Iron's
 * {@code onServerPreCast} and {@code onServerCastComplete}) need not share a handle. Starting with a key that is
 * already playing stops the old effect first. Keys are server-wide: stopping by key works even after the owner changed
 * dimension.
 */
public record FxKey(UUID owner, String slot) {
    public static FxKey of(Entity owner, String slot) {
        return new FxKey(owner.getUUID(), slot);
    }
}
