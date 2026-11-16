package net.deizyn.bhfx.api.client;

import net.deizyn.bhfx.api.BodyPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/** What an effect can use while ticking (20 times per second). Valid only during the call. */
public interface FxContext {
    /** The level the effect plays in. */
    ClientLevel level();

    /** The caster from {@code FxSpawn.caster}, when this client has it loaded. */
    Optional<Entity> caster();

    /** Current position: the followed body part or entity, else the origin. */
    Vec3 position();

    /** Where {@code part} of the followed entity is (newest frame); empty when nothing is followed. */
    Optional<BodyPose> part(BodyPart part);

    /** Where {@code part} of any entity is (newest frame, else an estimate). */
    BodyPose part(Entity entity, BodyPart part);

    /** Camera position in world space. */
    Vec3 cameraPos();

    void sound(SoundEvent sound, Vec3 at, float volume, float pitch);

    /** Respects the player's particle setting. */
    void particle(ParticleOptions particle, Vec3 at, Vec3 velocity);

    /** Camera shake that fades with distance from {@code source}. */
    void shake(Vec3 source, float amount, double range);

    /** Speed of sound for the wave methods, blocks per second (a block is a metre). */
    float SPEED_OF_SOUND = 343f;

    /**
     * {@link #sound}, but it travels: it plays when its wave reaches this player, {@code distance / 343} seconds
     * later, so someone far off sees the flash first and hears the boom after (thunder). Rounded to ticks.
     */
    @ApiStatus.Experimental
    default void soundWave(SoundEvent sound, Vec3 at, float volume, float pitch) {
        BHFxClient.soundWave(sound, at, volume, pitch, SPEED_OF_SOUND);
    }

    /** {@link #soundWave(SoundEvent, Vec3, float, float)} at another speed (blocks per second; 0 = instant). */
    @ApiStatus.Experimental
    default void soundWave(SoundEvent sound, Vec3 at, float volume, float pitch, float speed) {
        BHFxClient.soundWave(sound, at, volume, pitch, speed);
    }

    /**
     * A travelling sound in three versions by this player's distance: {@code near} under {@code midFrom} blocks,
     * {@code mid} under {@code farFrom}, else {@code far} (e.g. a sharp crack close by, a rolling boom far off).
     */
    @ApiStatus.Experimental
    default void soundWave(SoundEvent near, SoundEvent mid, SoundEvent far, double midFrom, double farFrom, Vec3 at,
                           float volume, float pitch) {
        BHFxClient.soundWave(near, mid, far, midFrom, farFrom, at, volume, pitch);
    }

    /** {@link #shake}, arriving with the wave from {@code source} ({@code distance / 343} seconds later). */
    @ApiStatus.Experimental
    default void shakeWave(Vec3 source, float amount, double range) {
        BHFxClient.shakeWave(source, amount, range, SPEED_OF_SOUND);
    }

    /** {@link #shakeWave(Vec3, float, double)} at another speed (blocks per second; 0 = instant). */
    @ApiStatus.Experimental
    default void shakeWave(Vec3 source, float amount, double range, float speed) {
        BHFxClient.shakeWave(source, amount, range, speed);
    }

    /**
     * Seconds until a wave from {@code at} reaches this player at the speed of sound: to time anything else with it
     * (a dust front, a screen pulse, a shock ring drawn at {@code since * 343}).
     */
    @ApiStatus.Experimental
    default double waveDelay(Vec3 at) {
        return BHFxClient.waveDelay(at, SPEED_OF_SOUND);
    }

    /** False when post-processing is off (quality OFF, /bhfx off, or disabled after a GL error). Ticking still runs. */
    boolean postAvailable();
}
