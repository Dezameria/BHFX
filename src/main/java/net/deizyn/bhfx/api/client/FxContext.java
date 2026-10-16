package net.deizyn.bhfx.api.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** What an effect can use while ticking (20 times per second). Valid only during the call. */
public interface FxContext {
    ClientLevel level();

    Optional<Entity> caster();

    /** Current position: the followed entity's position, else the origin. */
    Vec3 position();

    Vec3 cameraPos();

    void sound(SoundEvent sound, Vec3 at, float volume, float pitch);

    /** Respects the player's particle setting. */
    void particle(ParticleOptions particle, Vec3 at, Vec3 velocity);

    /** Camera shake that fades with distance from {@code source}. */
    void shake(Vec3 source, float amount, double range);

    /** False when post-processing is off (quality OFF, /bhfx off, or disabled after a GL error). Ticking still runs. */
    boolean postAvailable();
}
