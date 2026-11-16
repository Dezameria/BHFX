package net.deizyn.bhfx.impl.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.deizyn.bhfx.BHFxMod;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * /bhfx shoot: screenshots of an effect at set times after it starts, into
 * {@code screenshots/bhfx/<fx>_<date-time>/NN_tS.SSs.png}, plus:
 * <ul>
 *   <li>{@code sheet.png}: every shot on one page (4 across, labelled), so one image shows the whole run;</li>
 *   <li>{@code shots.txt}: the command, camera, window, and per shot the requested and actual time and
 *       {@code /bhfx status} at that frame (GPU ms, particle slots ...);</li>
 *   <li>{@code screenshots/bhfx/latest.txt}: the newest folder; {@code last_batch.txt}: every folder of the last
 *       /bhfx shoots.</li>
 * </ul>
 * Times are on the game clock (ticks + partial tick, like an effect's own {@code seconds}), so a pause or a slow frame
 * doesn't shift them; at most one shot per frame. Taken at the end of the frame (after bhfx, the hand and the GUI); the
 * GUI is hidden for the run unless {@code gui:true} (then the hand and hotbar show but chat is hidden: it covered half
 * the shots), and put back after. One run at a time; queued runs (/bhfx shoots) start one after another. Render thread.
 */
final class FxShots {
    /** Shot times when none are given (seconds). */
    static final float[] DEFAULT = {0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f};

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int SHEET_COLUMNS = 4;
    private static final int SHEET_TILE_WIDTH = 640;

    @Nullable
    private static Run run;
    /** Runs waiting their turn (/bhfx shoots): each starts its effect and returns true, or false to skip it. */
    private static final Deque<Supplier<Boolean>> QUEUE = new ArrayDeque<>();
    private static final List<String> BATCH = new ArrayList<>();
    private static boolean batching;

    private FxShots() {
    }

    private static final class Run {
        final float[] times;
        final Path dir;
        final double start;
        final ResourceLocation level;
        final boolean restoreHideGui;
        final boolean changedGui;
        @Nullable
        ChatVisiblity restoreChat;
        final StringBuilder notes = new StringBuilder();
        final List<CompletableFuture<Void>> saves = new ArrayList<>();
        final List<String> files = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        int next;

        Run(float[] times, Path dir, double start, ResourceLocation level, boolean restoreHideGui, boolean changedGui) {
            this.times = times;
            this.dir = dir;
            this.start = start;
            this.level = level;
            this.restoreHideGui = restoreHideGui;
            this.changedGui = changedGui;
        }
    }

    /** A run is taking shots, or queued runs are waiting. */
    static boolean busy() {
        return run != null || !QUEUE.isEmpty();
    }

    static Path root() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("bhfx");
    }

    /** Start shooting {@code fx} (already started by the command); returns the folder, or throws when it can't. */
    static Path start(ResourceLocation fx, @Nullable float[] times, boolean gui, String command) throws IOException {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            throw new IOException("no world");
        }
        if (run != null) {
            finish("replaced by a new /bhfx shoot");
        }
        String folder = (fx.getNamespace() + "_" + fx.getPath()).replaceAll("[^0-9A-Za-z_.-]", "_") + "_"
                + LocalDateTime.now().format(STAMP);
        Path root = root();
        Path dir = root.resolve(folder);
        if (Files.exists(dir)) {
            dir = root.resolve(folder + "_" + (System.nanoTime() % 1000));
        }
        Files.createDirectories(dir);
        Files.writeString(root.resolve("latest.txt"), dir.getFileName() + System.lineSeparator(), StandardCharsets.UTF_8);
        if (batching) {
            BATCH.add(dir.getFileName().toString());
        }
        boolean hidden = mc.options.hideGui;
        boolean change = !gui && !hidden;
        if (change) {
            mc.options.hideGui = true;
        }
        Run r = new Run(times != null ? times : DEFAULT, dir, clock(mc), mc.level.dimension().location(), hidden, change);
        if (gui && mc.options.chatVisibility().get() != ChatVisiblity.HIDDEN) {
            r.restoreChat = mc.options.chatVisibility().get();
            mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        r.notes.append("command: /").append(command).append('\n')
                .append("fx: ").append(fx).append('\n')
                .append("when: ").append(LocalDateTime.now()).append('\n')
                .append(String.format(Locale.ROOT, "camera: %.2f %.2f %.2f yaw %.1f pitch %.1f, fov option %d%n",
                        eye.x, eye.y, eye.z, cam.getYRot(), cam.getXRot(), mc.options.fov().get()))
                .append("window: ").append(mc.getWindow().getWidth()).append('x').append(mc.getWindow().getHeight())
                .append(gui ? ", GUI shown (chat hidden)" : ", GUI hidden").append('\n')
                .append("shots (requested s -> taken at s | bhfx status that frame):\n");
        run = r;
        return dir;
    }

    /**
     * Queue runs to shoot one after another (/bhfx shoots). Each supplier starts its effect and its run (true), or
     * reports why it can't (false: skipped). The first starts now when nothing is running.
     */
    static void queue(List<Supplier<Boolean>> runs) {
        QUEUE.addAll(runs);
        BATCH.clear();
        batching = true;
        if (run == null) {
            startNext();
        }
    }

    /** End of a frame: take the next shot when its time has come. */
    static void onFrameEnd() {
        Run r = run;
        if (r == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(r.level)) {
            QUEUE.clear();
            finish("stopped: the world changed");
            return;
        }
        double elapsed = (clock(mc) - r.start) / 20.0;
        if (r.next < r.times.length && elapsed >= r.times[r.next]) {
            String name = String.format(Locale.ROOT, "%02d_t%.2fs.png", r.next + 1, r.times[r.next]);
            r.notes.append(String.format(Locale.ROOT, "  %s: %.2f -> %.3f | %s%n", name, r.times[r.next], elapsed,
                    ClientFxManager.status().replace('\n', ' ')));
            r.files.add(name);
            r.labels.add(String.format(Locale.ROOT, "%02d  t=%.2fs", r.next + 1, r.times[r.next]));
            r.saves.add(save(Screenshot.takeScreenshot(mc.getMainRenderTarget()), r.dir.resolve(name)));
            r.next++;
        }
        if (r.next >= r.times.length) {
            finish(null);
            startNext();
        }
    }

    /** Drop a run and the queue without a message (logging out). */
    static void clear() {
        Run r = run;
        run = null;
        QUEUE.clear();
        batching = false;
        if (r != null) {
            restore(r);
        }
    }

    private static void startNext() {
        while (run == null && !QUEUE.isEmpty()) {
            Supplier<Boolean> next = QUEUE.poll();
            try {
                next.get();
            } catch (RuntimeException ex) {
                BHFxMod.LOGGER.warn("bhfx shoots: a queued run failed to start: {}", ex.toString());
            }
        }
        if (run == null && batching) {
            batching = false;
            List<String> folders = List.copyOf(BATCH);
            Path file = root().resolve("last_batch.txt");
            Util.ioPool().execute(() -> {
                try {
                    Files.writeString(file, String.join(System.lineSeparator(), folders) + System.lineSeparator(),
                            StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    BHFxMod.LOGGER.warn("bhfx shoots: could not write {}: {}", file, ex.getMessage());
                }
            });
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("bhfx shoots: done, " + folders.size()
                        + " folder(s) listed in screenshots/bhfx/last_batch.txt"), false);
            }
        }
    }

    private static void restore(Run r) {
        Minecraft mc = Minecraft.getInstance();
        if (r.changedGui) {
            mc.options.hideGui = r.restoreHideGui;
        }
        if (r.restoreChat != null) {
            mc.options.chatVisibility().set(r.restoreChat);
        }
    }

    private static void finish(@Nullable String why) {
        Run r = run;
        run = null;
        if (r == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        restore(r);
        if (why != null) {
            r.notes.append(why).append('\n');
        }
        String notes = r.notes.toString();
        Path dir = r.dir;
        List<String> files = List.copyOf(r.files);
        List<String> labels = List.copyOf(r.labels);
        // after every shot is on disk: the contact sheet, then the notes (their presence means the run is complete)
        CompletableFuture.allOf(r.saves.toArray(new CompletableFuture[0])).whenCompleteAsync((ok, err) -> {
            try {
                if (!files.isEmpty()) {
                    sheet(dir, files, labels);
                }
            } catch (IOException | RuntimeException ex) {
                BHFxMod.LOGGER.warn("bhfx shoot: could not build {}: {}", dir.resolve("sheet.png"), ex.toString());
            }
            try {
                Files.writeString(dir.resolve("shots.txt"), notes, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                BHFxMod.LOGGER.warn("bhfx shoot: could not write notes in {}: {}", dir, ex.getMessage());
            }
        }, Util.ioPool());
        if (mc.player != null) {
            String path = dir.toAbsolutePath().toString();
            Component link = Component.literal("screenshots/bhfx/" + dir.getFileName())
                    .withStyle(s -> s.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path)));
            mc.player.displayClientMessage(Component.literal("bhfx shoot: " + r.next + " of " + r.times.length
                    + " shot(s)" + (why != null ? " (" + why + ")" : "") + " in ").append(link), false);
        }
        BHFxMod.LOGGER.info("bhfx shoot: {} of {} shot(s) in {}{}", r.next, r.times.length, dir,
                why != null ? " (" + why + ")" : "");
    }

    /** {@code sheet.png}: the shots scaled to {@link #SHEET_TILE_WIDTH}, {@link #SHEET_COLUMNS} across, labelled. */
    static void sheet(Path dir, List<String> files, List<String> labels) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        for (String f : files) {
            BufferedImage img = ImageIO.read(dir.resolve(f).toFile());
            if (img != null) {
                images.add(img);
            }
        }
        if (images.isEmpty()) {
            return;
        }
        int w = SHEET_TILE_WIDTH;
        int h = Math.max(1, Math.round(w * images.get(0).getHeight() / (float) images.get(0).getWidth()));
        int cols = Math.min(SHEET_COLUMNS, images.size());
        int rows = (images.size() + cols - 1) / cols;
        BufferedImage out = new BufferedImage(w * cols, h * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            for (int i = 0; i < images.size(); i++) {
                int x = (i % cols) * w;
                int y = (i / cols) * h;
                g.drawImage(images.get(i), x, y, w, h, null);
                g.setColor(new Color(0, 0, 0, 160));
                g.fillRect(x, y, 150, 26);
                g.setColor(Color.YELLOW);
                g.drawString(i < labels.size() ? labels.get(i) : files.get(i), x + 6, y + 19);
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x, y, w - 1, h - 1);
            }
        } finally {
            g.dispose();
        }
        ImageIO.write(out, "png", dir.resolve("sheet.png").toFile());
    }

    private static CompletableFuture<Void> save(NativeImage image, Path file) {
        return CompletableFuture.runAsync(() -> {
            try (image) {
                image.writeToFile(file);
            } catch (IOException ex) {
                BHFxMod.LOGGER.warn("bhfx shoot: could not save {}: {}", file, ex.getMessage());
            }
        }, Util.ioPool());
    }

    /** The game clock in ticks, with the partial tick (stops while paused, like effects). */
    private static double clock(Minecraft mc) {
        return mc.level.getGameTime() + (double) mc.getFrameTime();
    }
}
