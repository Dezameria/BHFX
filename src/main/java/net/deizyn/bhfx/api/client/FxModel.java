package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A Blockbench / GeckoLib model ({@code .geo.json}) that an effect draws itself with {@link PassList#model}, with a
 * material shader of its own: no entity, nothing sent over the network, drawn inside bhfx's frame like particles
 * (hidden by the world in front of it, bloom on bright colours). Bones stay as modelled unless an {@link FxPose}
 * turns, moves, scales or hides them; {@link FxAnimation} plays a Blockbench {@code .animation.json} into one.
 * <ul>
 *   <li>{@link #geo}: {@code "ns:name"} loads {@code assets/ns/geo/name.geo.json} (where GeckoLib keeps them), so a
 *       model made for GeckoLib works as is. Baked like GeckoLib bakes it, so its texture lines up.</li>
 *   <li>{@link #texture}: the model's texture, bound as {@code MTexture} for the material.</li>
 *   <li>Materials: {@link #TEXTURED} (as painted, shaded and lit by the world's light at its origin), {@link #GLOW}
 *       (a glowing shell, rim brighter), {@link #DISSOLVE} (burns away by noise: {@code MParams.x} 0 whole .. 1 gone,
 *       {@code MParams.y} edge width, {@code MColor} the edge's colour). Or your own: {@code "ns:name"} loads
 *       {@code assets/ns/shaders/bhfx/model/name.fsh} defining {@code vec4 material(Surface s)}.</li>
 *   <li>Loaded off the render thread the first time it is drawn (it shows from the next frames), kept while used,
 *       freed a minute after its last use.</li>
 *   <li>Off with the rest of bhfx's drawing (quality OFF, shader pack, GL error): keep anything players must see as an
 *       entity, or check {@link FxContext#postAvailable()}.</li>
 * </ul>
 */
@ApiStatus.Experimental
public final class FxModel {
    public static final ResourceLocation TEXTURED = new ResourceLocation("bhfx", "textured");
    public static final ResourceLocation GLOW = new ResourceLocation("bhfx", "glow");
    public static final ResourceLocation DISSOLVE = new ResourceLocation("bhfx", "dissolve");
    /**
     * Clear glass / hot air: the model shows the image behind it bent by its surface. {@code MParams}: bend (blocks
     * of sideways push at the rim, e.g. 0.15), ripple (moving wobble, 0.5 = heat haze), ripples per block (0 = 3),
     * rim light. {@code MColor}: tint of what is seen through, alpha = how much of the bend shows. Draw it with a
     * later {@link PassOptions#order()} than the models it should bend: it sees only what was drawn before its run.
     */
    public static final ResourceLocation DISTORT = new ResourceLocation("bhfx", "distort");

    private final ResourceLocation file;
    @Nullable
    private final FxTexture texture;

    private FxModel(ResourceLocation file, @Nullable FxTexture texture) {
        this.file = file;
        this.texture = texture;
    }

    /** {@code "ns:name"} → {@code assets/ns/geo/name.geo.json}. */
    public static FxModel geo(ResourceLocation id) {
        return new FxModel(new ResourceLocation(id.getNamespace(), "geo/" + id.getPath() + ".geo.json"), null);
    }

    /** A model file by its full path inside the resources, e.g. {@code ns:geo/name.geo.json}. */
    public static FxModel file(ResourceLocation file) {
        return new FxModel(file, null);
    }

    /** This model with {@code texture} as its {@code MTexture} (pixel art: nearest filtering, clamped). */
    public FxModel texture(ResourceLocation textureFile) {
        return new FxModel(file, FxTexture.of(textureFile).nearest().clamp());
    }

    /** This model with any {@link FxTexture} as its {@code MTexture}. */
    public FxModel texture(FxTexture texture) {
        return new FxModel(file, texture);
    }

    public ResourceLocation file() {
        return file;
    }

    @Nullable
    public FxTexture texture() {
        return texture;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FxModel m && m.file.equals(file) && Objects.equals(m.texture, texture);
    }

    @Override
    public int hashCode() {
        return file.hashCode() * 31 + Objects.hashCode(texture);
    }

    @Override
    public String toString() {
        return "FxModel[" + file + (texture != null ? ", " + texture : "") + "]";
    }
}
