package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.impl.BHFxConfig;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Notices an active Iris/Oculus shader pack through Iris's public API (by reflection, so neither is a dependency).
 * Checked at most once a second; the answer only changes when the player switches packs.
 */
final class ShaderpackDetector {
    private static final long RECHECK_NANOS = 1_000_000_000L;

    private static boolean probed;
    @Nullable
    private static Object api;
    @Nullable
    private static Method inUse;
    private static boolean active;
    private static long checkedAt;

    private ShaderpackDetector() {
    }

    /** True while post effects should stay off because a shader pack is drawing the world. */
    static boolean blocksPost() {
        return BHFxConfig.OFF_WITH_SHADERPACK.get() && packActive();
    }

    static boolean packActive() {
        if (!probed) {
            probe();
        }
        if (inUse == null) {
            return false;
        }
        long now = System.nanoTime();
        if (now - checkedAt > RECHECK_NANOS) {
            checkedAt = now;
            boolean was = active;
            try {
                active = (Boolean) inUse.invoke(api);
            } catch (ReflectiveOperationException | RuntimeException e) {
                BHFxMod.LOGGER.warn("bhfx could not ask Iris/Oculus about shader packs; assuming none", e);
                inUse = null;
                active = false;
            }
            if (active != was) {
                BHFxMod.LOGGER.info("bhfx: shader pack {}; post effects {}", active ? "active" : "inactive",
                        active && BHFxConfig.OFF_WITH_SHADERPACK.get() ? "off" : "on");
            }
        }
        return active;
    }

    private static void probe() {
        probed = true;
        if (!ModList.get().isLoaded("oculus") && !ModList.get().isLoaded("iris")) {
            return;
        }
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | LinkageError e) {
            BHFxMod.LOGGER.warn("bhfx found Iris/Oculus but not its API; shader packs will not be detected", e);
        }
    }
}
