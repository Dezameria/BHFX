package net.deizyn.bhfx.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Everything an effect needs when it starts. Build one per start:
 * <pre>{@code
 * FxSpawn.at(caster.position()).target(hit).caster(caster).spellLevel(level).param(FxParam.RADIUS, 6f)
 * }</pre>
 */
public final class FxSpawn {
    /** Marker: use the effect's own {@code defaultDuration()}. */
    public static final int DEFAULT_DURATION = Integer.MIN_VALUE;
    /** Duration that never ends on its own; stop it explicitly. */
    public static final int UNTIL_STOPPED = -1;
    public static final double DEFAULT_RANGE = 128.0;

    private final Vec3 origin;
    private Vec3 target;
    private int casterId = -1;
    private int followId = -1;
    private Follow follow = Follow.NONE;
    @Nullable
    private Long seed;
    private final FxParams.Builder params = FxParams.builder();
    private int duration = DEFAULT_DURATION;
    private double range = DEFAULT_RANGE;
    private Audience audience = Audience.tracking();
    @Nullable
    private Boolean persistent;

    private FxSpawn(Vec3 origin) {
        this.origin = Objects.requireNonNull(origin);
        this.target = origin;
    }

    public static FxSpawn at(Vec3 origin) {
        return new FxSpawn(origin);
    }

    public FxSpawn target(Vec3 target) {
        this.target = Objects.requireNonNull(target);
        return this;
    }

    public FxSpawn caster(Entity caster) {
        this.casterId = caster.getId();
        return this;
    }

    public FxSpawn follow(Entity entity, Follow mode) {
        this.followId = mode == Follow.NONE ? -1 : entity.getId();
        this.follow = mode;
        return this;
    }

    public FxSpawn seed(long seed) {
        this.seed = seed;
        return this;
    }

    public <T> FxSpawn param(FxParam<T> key, T value) {
        params.put(key, value);
        return this;
    }

    public FxSpawn params(FxParams values) {
        params.putAll(values);
        return this;
    }

    public FxSpawn spellLevel(int level) {
        return param(FxParam.SPELL_LEVEL, level);
    }

    /** Ticks; {@link #UNTIL_STOPPED} for effects that end only when stopped. */
    public FxSpawn duration(int ticks) {
        this.duration = ticks;
        return this;
    }

    /** Broadcast radius in blocks (server only). */
    public FxSpawn range(double blocks) {
        this.range = Math.max(1.0, blocks);
        return this;
    }

    /** Who receives it (server only). */
    public FxSpawn audience(Audience audience) {
        this.audience = Objects.requireNonNull(audience);
        return this;
    }

    /**
     * Server only. Persistent effects are also sent to players who come into range, log in or respawn while the
     * effect is still playing; they join it mid-timeline. Default: true when the duration is over 5 s or
     * {@link #UNTIL_STOPPED}.
     */
    public FxSpawn persistent(boolean persistent) {
        this.persistent = persistent;
        return this;
    }

    /** Whether the server should keep re-sending this effect to late joiners. */
    public boolean isPersistent() {
        if (persistent != null) {
            return persistent;
        }
        return duration == UNTIL_STOPPED || duration == DEFAULT_DURATION || duration > 100;
    }

    public Vec3 origin() { return origin; }
    public Vec3 target() { return target; }
    public int casterId() { return casterId; }
    public int followId() { return followId; }
    public Follow follow() { return follow; }
    @Nullable public Long seed() { return seed; }
    public FxParams builtParams() { return params.build(); }
    public int duration() { return duration; }
    public double range() { return range; }
    public Audience audience() { return audience; }
}
