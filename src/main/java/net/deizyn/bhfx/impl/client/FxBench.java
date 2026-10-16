package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.api.FxSpawn;
import net.deizyn.bhfx.api.StopMode;
import net.deizyn.bhfx.api.client.LocalFxHandle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * /bhfx bench: the effect replays locally where the player looks for the whole run, while bhfx's drawing is switched
 * off and on every second. Alternating cancels the slow drift of frame time (chunk loading, clock boost, other mods)
 * that a "first half without, second half with" run mistakes for the effect's cost. Local only — nothing is sent to
 * the server or other players.
 */
final class FxBench {
    private enum Phase { IDLE, WITHOUT, WITH }

    private static final long SEGMENT_NANOS = 1_000_000_000L;
    /** Frames ignored after each switch (the switch frame itself may allocate or recompile). */
    private static final int SETTLE_FRAMES = 3;

    private Phase phase = Phase.IDLE;
    private ResourceLocation fxId;
    private long endAt;
    private long phaseStart;
    private int settle;
    private long lastFrame;
    private Vec3 target = Vec3.ZERO;
    private LocalFxHandle handle = LocalFxHandle.NONE;
    private final Samples without = new Samples();
    private final Samples with = new Samples();
    private double gpuSum;
    private int gpuCount;

    boolean running() {
        return phase != Phase.IDLE;
    }

    void start(ResourceLocation fxId, int seconds) {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.player.pick(64.0, 1f, false);
        Vec3 eye = mc.player.getEyePosition();
        this.target = hit.getType() == HitResult.Type.MISS ? eye.add(mc.player.getLookAngle().scale(24.0)) : hit.getLocation();
        this.fxId = fxId;
        without.clear();
        with.clear();
        gpuSum = 0.0;
        gpuCount = 0;
        lastFrame = 0L;
        settle = SETTLE_FRAMES;
        phase = Phase.WITH; // start drawing first so shaders and targets exist before anything is measured
        phaseStart = System.nanoTime();
        endAt = phaseStart + seconds * 1_000_000_000L;
    }

    /** While true, bhfx skips drawing this frame (the effect keeps ticking). */
    boolean suppressing() {
        return phase == Phase.WITHOUT;
    }

    void cancel() {
        if (phase != Phase.IDLE) {
            phase = Phase.IDLE;
            ClientFxManager.stopLocal(handle, StopMode.IMMEDIATE);
            handle = LocalFxHandle.NONE;
        }
    }

    /** Every rendered frame, before effects draw. */
    void frame(long now) {
        if (phase == Phase.IDLE) {
            return;
        }
        if (lastFrame != 0L && settle <= 0) {
            (phase == Phase.WITHOUT ? without : with).add((now - lastFrame) / 1e6f);
        }
        settle--;
        lastFrame = now;
        if (now >= endAt) {
            cancel();
            report();
            return;
        }
        if (now - phaseStart >= SEGMENT_NANOS) {
            phase = phase == Phase.WITH ? Phase.WITHOUT : Phase.WITH;
            phaseStart = now;
            settle = SETTLE_FRAMES;
        }
    }

    /** Every client tick: keep the effect replaying during the second half. */
    void tick() {
        if (phase == Phase.IDLE || ClientFxManager.isLocalAlive(handle)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        handle = ClientFxManager.startLocal(fxId, FxSpawn.at(mc.player.position()).target(target).caster(mc.player));
        if (handle.isNone()) {
            cancel();
            message(Component.literal("bhfx bench: could not start " + fxId + " (not registered, or bhfx is off)")
                    .withStyle(ChatFormatting.RED));
        }
    }

    void gpu(double ms) {
        if (phase == Phase.WITH && ms >= 0) {
            gpuSum += ms;
            gpuCount++;
        }
    }

    private void report() {
        double a = without.mean();
        double b = with.mean();
        message(Component.literal("bhfx bench " + fxId + ":").withStyle(ChatFormatting.AQUA));
        message(Component.literal("  (bhfx drawing switched off/on every second while the effect played)"));
        message(Component.literal(String.format("  without: %.2f ms avg (%.0f fps), 1%% worst %.2f ms, %d frames",
                a, 1000.0 / a, without.percentile(0.99), without.count())));
        message(Component.literal(String.format("  with:    %.2f ms avg (%.0f fps), 1%% worst %.2f ms, %d frames",
                b, 1000.0 / b, with.percentile(0.99), with.count())));
        message(Component.literal(String.format("  cost: %+.2f ms per frame%s", b - a,
                gpuCount > 0 ? String.format(", bhfx GPU %.2f ms avg", gpuSum / gpuCount) : ""))
                .withStyle(ChatFormatting.YELLOW));
        double gpu = gpuCount > 0 ? gpuSum / gpuCount : 0.0;
        if (b - a > gpu * 2.0 + 0.5) {
            // measured in testing: drawing cost 0.3 ms, but shake / FOV made Minecraft re-cull chunks every frame (+1.7 ms)
            message(Component.literal("  Most of that is camera movement (shake / FOV punch make Minecraft re-cull chunks every frame), "
                    + "not drawing. For drawing alone: comfort.shakeScale = 0 and camera.control = NONE.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void message(Component c) {
        Minecraft.getInstance().gui.getChat().addMessage(c);
    }

    private static final class Samples {
        private float[] values = new float[1024];
        private int size;

        void add(float v) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = v;
        }

        void clear() {
            size = 0;
        }

        int count() {
            return size;
        }

        double mean() {
            double sum = 0.0;
            for (int i = 0; i < size; i++) {
                sum += values[i];
            }
            return size > 0 ? sum / size : 0.0;
        }

        double percentile(double p) {
            if (size == 0) {
                return 0.0;
            }
            float[] sorted = Arrays.copyOf(values, size);
            Arrays.sort(sorted);
            return sorted[Math.min(size - 1, (int) Math.floor(p * size))];
        }
    }
}
