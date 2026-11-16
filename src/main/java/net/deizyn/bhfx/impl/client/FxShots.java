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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * /bhfx shoot: screenshots of an effect at set times after it starts, into
 * {@code screenshots/bhfx/<fx>_<date-time>/NN_tS.SSs.png} with a {@code shots.txt} (command, camera, window, bhfx
 * status, requested and actual times) and {@code screenshots/bhfx/latest.txt} naming the newest folder. Times are on the
 * game clock (ticks + partial tick, like an effect's own {@code seconds}), so a pause or a slow frame doesn't shift
 * them; at most one shot per frame. Taken at the end of the frame (after bhfx, the hand and the GUI); the GUI is hidden
 * for the run unless {@code gui:true}, and put back after. One run at a time; a new one replaces it. Render thread.
 */
final class FxShots {
    /** Shot times when none are given (seconds). */
    static final float[] DEFAULT = {0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f};

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Nullable
    private static Run run;

    private FxShots() {
    }

    private static final class Run {
        final float[] times;
        final Path dir;
        final double start;
        final ResourceLocation level;
        final boolean restoreHideGui;
        final boolean changedGui;
        final StringBuilder notes = new StringBuilder();
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
        Path root = mc.gameDirectory.toPath().resolve("screenshots").resolve("bhfx");
        Path dir = root.resolve(folder);
        Files.createDirectories(dir);
        Files.writeString(root.resolve("latest.txt"), folder + System.lineSeparator(), StandardCharsets.UTF_8);
        boolean hidden = mc.options.hideGui;
        boolean change = !gui && !hidden;
        if (change) {
            mc.options.hideGui = true;
        }
        Run r = new Run(times != null ? times : DEFAULT, dir, clock(mc), mc.level.dimension().location(), hidden, change);
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        r.notes.append("command: /").append(command).append('\n')
                .append("fx: ").append(fx).append('\n')
                .append("when: ").append(LocalDateTime.now()).append('\n')
                .append(String.format(Locale.ROOT, "camera: %.2f %.2f %.2f yaw %.1f pitch %.1f, fov option %d%n",
                        eye.x, eye.y, eye.z, cam.getYRot(), cam.getXRot(), mc.options.fov().get()))
                .append("window: ").append(mc.getWindow().getWidth()).append('x').append(mc.getWindow().getHeight())
                .append(gui ? ", GUI shown" : ", GUI hidden").append('\n')
                .append("bhfx: ").append(ClientFxManager.status().replace('\n', ' ')).append('\n')
                .append("shots (requested s -> taken at s):\n");
        run = r;
        return dir;
    }

    /** End of a frame: take the next shot when its time has come. */
    static void onFrameEnd() {
        Run r = run;
        if (r == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(r.level)) {
            finish("stopped: the world changed");
            return;
        }
        double elapsed = (clock(mc) - r.start) / 20.0;
        if (r.next < r.times.length && elapsed >= r.times[r.next]) {
            String name = String.format(Locale.ROOT, "%02d_t%.2fs.png", r.next + 1, r.times[r.next]);
            r.notes.append(String.format(Locale.ROOT, "  %s: %.2f -> %.3f%n", name, r.times[r.next], elapsed));
            save(Screenshot.takeScreenshot(mc.getMainRenderTarget()), r.dir.resolve(name));
            r.next++;
        }
        if (r.next >= r.times.length) {
            finish(null);
        }
    }

    /** Drop a run without a message (logging out). */
    static void clear() {
        Run r = run;
        run = null;
        if (r != null && r.changedGui) {
            Minecraft.getInstance().options.hideGui = r.restoreHideGui;
        }
    }

    private static void finish(@Nullable String why) {
        Run r = run;
        run = null;
        if (r == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (r.changedGui) {
            mc.options.hideGui = r.restoreHideGui;
        }
        if (why != null) {
            r.notes.append(why).append('\n');
        }
        String notes = r.notes.toString();
        Path file = r.dir.resolve("shots.txt");
        Util.ioPool().execute(() -> {
            try {
                Files.writeString(file, notes, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                BHFxMod.LOGGER.warn("bhfx shoot: could not write {}: {}", file, ex.getMessage());
            }
        });
        if (mc.player != null) {
            String path = r.dir.toAbsolutePath().toString();
            Component link = Component.literal("screenshots/bhfx/" + r.dir.getFileName())
                    .withStyle(s -> s.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path)));
            mc.player.displayClientMessage(Component.literal("bhfx shoot: " + r.next + " of " + r.times.length
                    + " shot(s)" + (why != null ? " (" + why + ")" : "") + " in ").append(link), false);
        }
        BHFxMod.LOGGER.info("bhfx shoot: {} of {} shot(s) in {}{}", r.next, r.times.length, r.dir,
                why != null ? " (" + why + ")" : "");
    }

    private static void save(NativeImage image, Path file) {
        Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
            } catch (IOException ex) {
                BHFxMod.LOGGER.warn("bhfx shoot: could not save {}: {}", file, ex.getMessage());
            }
        });
    }

    /** The game clock in ticks, with the partial tick (stops while paused, like effects). */
    private static double clock(Minecraft mc) {
        return mc.level.getGameTime() + (double) mc.getFrameTime();
    }
}
