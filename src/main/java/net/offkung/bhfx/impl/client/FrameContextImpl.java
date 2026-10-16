package net.offkung.bhfx.impl.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhfx.api.client.Fx;
import net.offkung.bhfx.api.client.FrameContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.Optional;

/** Reused frame context; begun once per frame, rebound for each effect. */
final class FrameContextImpl implements FrameContext {
    private final Vector4f scratch = new Vector4f();
    private float partial;
    private Vec3 camera = Vec3.ZERO;
    private int width;
    private int height;
    private Matrix4f viewProj;
    private float projScaleY = 1f;
    private Fx fx;
    @Nullable
    private Entity followed;

    void begin(float partial, Vec3 camera, int width, int height, Matrix4f viewProj, float projScaleY) {
        this.partial = partial;
        this.camera = camera;
        this.width = width;
        this.height = height;
        this.viewProj = viewProj;
        this.projScaleY = projScaleY;
    }

    /** Projection's vertical scale (1 / tan(fov / 2)). */
    float projScaleY() {
        return projScaleY;
    }

    FrameContextImpl bind(Fx fx, @Nullable Entity followed) {
        this.fx = fx;
        this.followed = followed;
        return this;
    }

    @Override
    public float partialTick() {
        return partial;
    }

    @Override
    public Vec3 cameraPos() {
        return camera;
    }

    @Override
    public Vec3 rel(Vec3 world) {
        return world.subtract(camera);
    }

    @Override
    public Vec3 position() {
        return followed != null ? followed.getPosition(partial) : fx.init().origin();
    }

    @Override
    public Optional<Vector2f> screenUv(Vec3 world) {
        Vec3 r = rel(world);
        scratch.set((float) r.x, (float) r.y, (float) r.z, 1f);
        viewProj.transform(scratch);
        if (scratch.w <= 0.05f) {
            return Optional.empty();
        }
        return Optional.of(new Vector2f(scratch.x / scratch.w * 0.5f + 0.5f, scratch.y / scratch.w * 0.5f + 0.5f));
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }
}
