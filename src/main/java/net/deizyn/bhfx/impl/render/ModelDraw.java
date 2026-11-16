package net.deizyn.bhfx.impl.render;

import net.deizyn.bhfx.api.client.FxModel;
import net.deizyn.bhfx.api.client.FxPose;
import org.jetbrains.annotations.Nullable;

/**
 * One model to draw this frame: where (camera-relative origin, {@code axes} = the frame's x, y, z axes times the
 * scale, column-major like a GLSL {@code mat3}) and the world light at its origin (0..1). With {@code instances}, it is
 * drawn {@code count} times, each copy placed by the table instead (origin and axes then only give the light's place).
 * {@code pose}: bones moved on the GPU (every copy alike), or null for the model as baked.
 */
public record ModelDraw(FxModel model, float ox, float oy, float oz, float[] axes, float scale, float light,
                        @Nullable DataTable instances, int count, @Nullable FxPose pose,
                        @Nullable ParticleBatch pieces) {
    public ModelDraw(FxModel model, float ox, float oy, float oz, float[] axes, float scale, float light,
                     @Nullable DataTable instances, int count, @Nullable FxPose pose) {
        this(model, ox, oy, oz, axes, scale, light, instances, count, pose, null);
    }

    public ModelDraw(FxModel model, float ox, float oy, float oz, float[] axes, float scale, float light,
                     @Nullable FxPose pose) {
        this(model, ox, oy, oz, axes, scale, light, null, 0, pose);
    }
}
