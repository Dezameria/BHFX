package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.BHFxMod;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes Figura avatars behave in the mirror pass, by reflection so Figura stays optional.
 *
 * <ul>
 *   <li>{@code Avatar.culled = false}: builds that cull avatars outside the camera view (the BeeHouse Figura build)
 *       would otherwise drop the avatar of anyone behind the camera, and the vanilla skin would show instead.</li>
 *   <li>{@code Avatar.renderScriptsSkipped = true}: the same switch that build uses to throttle render scripts. The
 *       RENDER / POST_RENDER events and part scripts don't run for the mirror pass, so the reflection reuses the part
 *       state the last real pass left and physics scripts are not stepped twice per frame.</li>
 * </ul>
 * Fields a Figura version doesn't have are skipped. The mirror pass runs before the world's entity pass, so the part
 * matrices that scripts read next frame ({@code partToWorldMatrix}) still come from the real, unmirrored render.
 */
final class FiguraMirror {
    private static boolean probed;
    @Nullable
    private static Method avatarFor;
    @Nullable
    private static Field culled;
    @Nullable
    private static Field scriptsSkipped;

    private FiguraMirror() {
    }

    /** Prepares the avatars of {@code players} for the mirror pass; run the result to put them back. */
    static Runnable begin(List<Player> players) {
        if (!probed) {
            probe();
        }
        if (avatarFor == null) {
            return () -> {
            };
        }
        List<Object> avatars = new ArrayList<>();
        List<boolean[]> saved = new ArrayList<>();
        try {
            for (Player player : players) {
                Object avatar = avatarFor.invoke(null, player.getUUID());
                if (avatar == null) {
                    continue;
                }
                boolean[] old = {culled != null && culled.getBoolean(avatar),
                        scriptsSkipped != null && scriptsSkipped.getBoolean(avatar)};
                avatars.add(avatar);
                saved.add(old);
                if (culled != null) {
                    culled.setBoolean(avatar, false);
                }
                if (scriptsSkipped != null) {
                    scriptsSkipped.setBoolean(avatar, true);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
        }
        return () -> {
            try {
                for (int i = 0; i < avatars.size(); i++) {
                    if (culled != null) {
                        culled.setBoolean(avatars.get(i), saved.get(i)[0]);
                    }
                    if (scriptsSkipped != null) {
                        scriptsSkipped.setBoolean(avatars.get(i), saved.get(i)[1]);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                disable(e);
            }
        };
    }

    private static void probe() {
        probed = true;
        if (!ModList.get().isLoaded("figura")) {
            return;
        }
        try {
            Class<?> manager = Class.forName("org.figuramc.figura.avatar.AvatarManager");
            Class<?> avatar = Class.forName("org.figuramc.figura.avatar.Avatar");
            avatarFor = manager.getMethod("getAvatarForPlayer", java.util.UUID.class);
            culled = optionalField(avatar, "culled");
            scriptsSkipped = optionalField(avatar, "renderScriptsSkipped");
            BHFxMod.LOGGER.info("bhfx: Figura found; mirror {} view culling, {} render scripts",
                    culled != null ? "overrides" : "has no", scriptsSkipped != null ? "reuses" : "reruns");
        } catch (ReflectiveOperationException | LinkageError e) {
            BHFxMod.LOGGER.warn("bhfx found Figura but not its avatar API; mirrors draw avatars as Figura decides", e);
            avatarFor = null;
        }
    }

    @Nullable
    private static Field optionalField(Class<?> type, String name) {
        try {
            Field field = type.getField(name);
            return field.getType() == boolean.class ? field : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static void disable(Exception e) {
        BHFxMod.LOGGER.warn("bhfx: Figura mirror support failed; turned off for this session", e);
        avatarFor = null;
    }
}
