package net.deizyn.bhfx.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.deizyn.bhfx.BHFxMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Loaded {@code .animation.json} files for {@code FxAnimation}: each read and parsed once on Minecraft's background
 * executor, shared by every effect, dropped on resource reload ({@link #generation()} goes up, so holders fetch again).
 * Render thread only.
 */
public final class AnimationLibrary {
    private static final Map<ResourceLocation, CompletableFuture<GeoAnimationLoader.Library>> FILES = new HashMap<>();
    private static final GeoAnimationLoader.Library FAILED = new GeoAnimationLoader.Library(Map.of(), List.of());
    private static int generation;

    private AnimationLibrary() {
    }

    public static int generation() {
        return generation;
    }

    /** The file's clips, or null while it loads (started on the first call). Empty when it failed (logged once). */
    @Nullable
    public static GeoAnimationLoader.Library get(ResourceLocation file) {
        CompletableFuture<GeoAnimationLoader.Library> f = FILES.computeIfAbsent(file, AnimationLibrary::start);
        return f.isDone() ? f.join() : null;
    }

    public static void clear() {
        RenderSystem.assertOnRenderThread();
        FILES.clear();
        generation++;
    }

    private static CompletableFuture<GeoAnimationLoader.Library> start(ResourceLocation file) {
        return CompletableFuture.supplyAsync(() -> read(file), Util.backgroundExecutor()).handle((lib, ex) -> {
            if (ex != null) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                BHFxMod.LOGGER.error("bhfx animation {} failed to load: {}", file, cause.getMessage());
                return FAILED;
            }
            for (String w : lib.warnings()) {
                BHFxMod.LOGGER.warn("bhfx animation {}: {}", file, w);
            }
            return lib;
        });
    }

    private static GeoAnimationLoader.Library read(ResourceLocation file) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            throw new IllegalStateException("missing " + file);
        }
        try (InputStream in = resource.get().open()) {
            return GeoAnimationLoader.load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }
}
