package net.deizyn.bhfx.impl.render;

import net.deizyn.bhfx.api.client.FxModel;
import org.jetbrains.annotations.Nullable;

/**
 * One model to draw this frame: where (camera-relative origin, {@code axes} = the frame's x, y, z axes times the
 * scale, column-major like a GLSL {@code mat3}) and the world light at its origin (0..1). With {@code instances}, it is
 * drawn {@code count} times, each copy placed by the table instead (origin and axes then only give the light's place).
 */
public record ModelDraw(FxModel model, float ox, float oy, float oz, float[] axes, float scale, float light,
                        @Nullable DataTable instances, int count) {
    public ModelDraw(FxModel model, float ox, float oy, float oz, float[] axes, float scale, float light) {
        this(model, ox, oy, oz, axes, scale, light, null, 0);
    }
}
