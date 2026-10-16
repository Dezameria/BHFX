package net.deizyn.bhfx.impl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.Fx;
import net.deizyn.bhfx.api.client.FxContext;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Reused tick context; rebound for each effect. */
final class FxContextImpl implements FxContext {
    private ClientLevel level;
    private Fx fx;
    @Nullable
    private Entity followed;

    FxContextImpl bind(ClientLevel level, Fx fx, @Nullable Entity followed) {
        this.level = level;
        this.fx = fx;
        this.followed = followed;
        return this;
    }

    @Override
    public ClientLevel level() {
        return level;
    }

    @Override
    public Optional<Entity> caster() {
        int id = fx.init().casterId();
        return id < 0 ? Optional.empty() : Optional.ofNullable(level.getEntity(id));
    }

    @Override
    public Vec3 position() {
        return followed != null ? followed.position() : fx.init().origin();
    }

    @Override
    public Vec3 cameraPos() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    @Override
    public void sound(SoundEvent sound, Vec3 at, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    @Override
    public void particle(ParticleOptions particle, Vec3 at, Vec3 velocity) {
        level.addParticle(particle, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }

    @Override
    public void shake(Vec3 source, float amount, double range) {
        ClientFxManager.shake(source, amount, range);
    }

    @Override
    public boolean postAvailable() {
        return ClientFxManager.postAvailable();
    }
}
