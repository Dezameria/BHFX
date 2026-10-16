package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.impl.render.PostRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Compiles every bhfx pass shader (from every namespace) one per frame after a world loads, so the first cast does
 * not stall on shader compilation. Finds them by listing {@code assets/<ns>/shaders/bhfx/**.fsh}; include files are
 * compiled as part of the passes that use them.
 */
final class ShaderWarmup {
    private static final String ROOT = "shaders/bhfx/";
    private static final String SUFFIX = ".fsh";

    private final Deque<ResourceLocation> queue = new ArrayDeque<>();
    private int total;
    private int failed;
    private long spentNanos;
    private long worstNanos;
    private boolean running;

    /** Queue every pass shader; internal passes first because every frame with effects uses them. */
    void schedule() {
        queue.clear();
        List<ResourceLocation> found = new ArrayList<>();
        Minecraft.getInstance().getResourceManager()
                .listResources("shaders/bhfx", rl -> rl.getPath().endsWith(SUFFIX) && !rl.getPath().contains("/include/"))
                .keySet()
                .forEach(file -> {
                    String path = file.getPath();
                    found.add(new ResourceLocation(file.getNamespace(), path.substring(ROOT.length(), path.length() - SUFFIX.length())));
                });
        for (String internal : new String[]{"composite", "bloom_down", "bloom_up"}) {
            ResourceLocation id = BHFxMod.id(internal);
            if (found.remove(id)) {
                queue.add(id);
            }
        }
        found.sort(null);
        queue.addAll(found);
        total = queue.size();
        failed = 0;
        spentNanos = 0L;
        worstNanos = 0L;
        running = total > 0;
    }

    void cancel() {
        queue.clear();
        running = false;
    }

    boolean running() {
        return running;
    }

    /** Warm one shader. Call once per frame on the render thread. */
    void step(PostRenderer renderer) {
        ResourceLocation id = queue.poll();
        if (id == null) {
            finish(renderer);
            return;
        }
        long start = System.nanoTime();
        if (!renderer.warmup(id)) {
            failed++;
        }
        long took = System.nanoTime() - start;
        spentNanos += took;
        worstNanos = Math.max(worstNanos, took);
        if (queue.isEmpty()) {
            finish(renderer);
        }
    }

    private void finish(PostRenderer renderer) {
        running = false;
        renderer.endWarmup();
        BHFxMod.LOGGER.info("bhfx warmed up {} shader(s) in {} ms (slowest {} ms){}", total - failed,
                spentNanos / 1_000_000L, worstNanos / 1_000_000L, failed > 0 ? ", " + failed + " broken (see above)" : "");
    }

    String describe() {
        return running ? "warming shaders " + (total - queue.size()) + "/" + total : "";
    }
}
