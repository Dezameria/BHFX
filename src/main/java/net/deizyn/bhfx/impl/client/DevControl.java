package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.BHFxMod;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dev-only remote control, so a tool (Claude) can drive a running dev client without anyone typing: commands are read
 * from {@code <gameDir>/bhfx_dev/inbox/*.txt}. Off in production jars ({@link FMLEnvironment#production}) and off until
 * the folder {@code bhfx_dev} exists. Each file runs line by line, files in name order; a finished file moves to
 * {@code inbox/done/}, and what each line did goes to {@code bhfx_dev/outbox/<file>.log}.
 * <p>
 * Lines: {@code # comment}; {@code wait <seconds>}; {@code camera first|back|front}; {@code status} (logs fps and
 * {@code /bhfx status} now); {@code stats [seconds]} (logs {@code /bhfx stats} averaged over the last 1..10 s);
 * {@code fps <10..260>|unlimited|restore} (frame cap and vsync, put back by restore or quit); {@code quit} (closes the game);
 * otherwise a command, with or without its slash, whose first word is in {@link #ALLOWED} ({@code bhfx}, {@code tp},
 * {@code time}, {@code weather}, {@code fill} ...), sent as if typed in chat. A line waits while a /bhfx shoot is taking
 * shots, so a list of shoots runs one by one. Nothing runs before the player is in a world.
 * <p>
 * {@code bhfx_dev/status.txt}, every second: in a world or not, position and view, camera type, open screen, shoot
 * busy, the inbox file and line, the newest shot folder, {@code /bhfx status}. While on, the game does not pause when
 * its window loses focus (a paused world would hold the shots forever) and a pause menu is closed before each line.
 */
final class DevControl {
    /** First words a line may start with. */
    static final Set<String> ALLOWED = Set.of("bhfx", "tp", "teleport", "time", "weather", "gamemode", "gamerule",
            "summon", "kill", "effect", "difficulty", "fill", "setblock", "clear", "give");

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final long STATUS_EVERY_NANOS = 1_000_000_000L;
    private static final long POLL_EVERY_NANOS = 250_000_000L;

    private static boolean checked;
    @Nullable
    private static Path root;
    private static long lastStatus;
    private static long lastPoll;
    private static long waitUntil;
    @Nullable
    private static String file;
    private static final Deque<String> LINES = new ArrayDeque<>();
    private static int lineNo;
    private static boolean waitingForWindow;
    /** Frame cap and vsync before the first {@code fps} line; -1 when untouched. */
    private static int savedFps = -1;
    private static boolean savedVsync;

    private DevControl() {
    }

    /** Client tick end. */
    static void tick() {
        Path dir = root();
        if (dir == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        long now = System.nanoTime();
        if (now - lastStatus >= STATUS_EVERY_NANOS) {
            lastStatus = now;
            writeStatus(dir, mc);
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || now < waitUntil || FxShots.busy()) {
            return;
        }
        if (LINES.isEmpty()) {
            if (file != null) {
                log(dir, "-- end of " + file);
                file = null;
            }
            if (now - lastPoll < POLL_EVERY_NANOS) {
                return;
            }
            lastPoll = now;
            nextFile(dir);
            return;
        }
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (mc.getWindow().getWidth() < 64 || mc.getWindow().getHeight() < 64) {
            // minimised: nothing is drawn, so shots and readings would be worthless; wait for the window to come back
            if (!waitingForWindow) {
                waitingForWindow = true;
                log(dir, "waiting: the window is minimised");
            }
            return;
        }
        waitingForWindow = false;
        String line = LINES.poll();
        lineNo++;
        run(dir, mc, player, line.trim());
    }

    @Nullable
    private static Path root() {
        if (!checked) {
            checked = true;
            if (FMLEnvironment.production) {
                return null;
            }
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("bhfx_dev");
            if (Files.isDirectory(dir)) {
                root = dir;
                BHFxMod.LOGGER.info("bhfx dev control on: reading {}", dir.resolve("inbox"));
            }
        }
        return root;
    }

    private static void nextFile(Path dir) {
        Path inbox = dir.resolve("inbox");
        if (!Files.isDirectory(inbox)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inbox, "*.txt")) {
            for (Path p : stream) {
                files.add(p);
            }
        } catch (IOException ex) {
            return;
        }
        if (files.isEmpty()) {
            return;
        }
        files.sort(null);
        Path next = files.get(0);
        try {
            List<String> lines = Files.readAllLines(next, StandardCharsets.UTF_8);
            Path done = inbox.resolve("done");
            Files.createDirectories(done);
            Files.move(next, done.resolve(next.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            file = next.getFileName().toString();
            lineNo = 0;
            LINES.clear();
            LINES.addAll(lines);
            log(dir, "-- start " + file + " (" + lines.size() + " lines)");
        } catch (IOException ex) {
            BHFxMod.LOGGER.warn("bhfx dev control: could not read {}: {}", next, ex.getMessage());
            try {
                Files.move(next, next.resolveSibling(next.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // left in place: retried next poll
            }
        }
    }

    private static void run(Path dir, Minecraft mc, LocalPlayer player, String line) {
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        String[] words = line.split("\\s+");
        String head = words[0].toLowerCase(Locale.ROOT);
        switch (head) {
            case "wait" -> {
                double s = words.length > 1 ? parse(words[1]) : Double.NaN;
                if (!(s >= 0.0) || s > 600.0) {
                    log(dir, lineNo + " refused (wait 0..600 s): " + line);
                    return;
                }
                waitUntil = System.nanoTime() + (long) (s * 1.0e9);
                log(dir, lineNo + " " + line);
                return;
            }
            case "camera" -> {
                CameraType type = words.length < 2 ? null : switch (words[1].toLowerCase(Locale.ROOT)) {
                    case "first" -> CameraType.FIRST_PERSON;
                    case "back" -> CameraType.THIRD_PERSON_BACK;
                    case "front" -> CameraType.THIRD_PERSON_FRONT;
                    default -> null;
                };
                if (type == null) {
                    log(dir, lineNo + " refused (camera first|back|front): " + line);
                    return;
                }
                mc.options.setCameraType(type);
                log(dir, lineNo + " " + line);
                return;
            }
            case "status" -> {
                // a reading to compare runs by: last second's fps and /bhfx status, into the outbox log
                log(dir, lineNo + " status: fps " + mc.getFps() + " | " + ClientFxManager.status().replace('\n', ' '));
                return;
            }
            case "stats" -> {
                double s = words.length > 1 ? parse(words[1]) : 10.0;
                if (!(s >= 1.0) || s > 10.0) {
                    log(dir, lineNo + " refused (stats 1..10 s): " + line);
                    return;
                }
                log(dir, lineNo + " stats:");
                for (String l : ClientFxManager.statsLines(s)) {
                    log(dir, "    " + l);
                }
                return;
            }
            case "fps" -> {
                String arg = words.length > 1 ? words[1].toLowerCase(Locale.ROOT) : "";
                if (arg.equals("restore")) {
                    restoreFps(mc);
                    log(dir, lineNo + " " + line);
                    return;
                }
                double n = arg.equals("unlimited") ? 260.0 : parse(arg);
                if (!(n >= 10.0) || n > 260.0) {
                    log(dir, lineNo + " refused (fps 10..260|unlimited|restore): " + line);
                    return;
                }
                if (savedFps < 0) {
                    savedFps = mc.options.framerateLimit().get();
                    savedVsync = mc.options.enableVsync().get();
                }
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set((int) n);
                log(dir, lineNo + " " + line + " (vsync off; was " + savedFps + (savedVsync ? ", vsync on)" : ", vsync off)"));
                return;
            }
            case "quit" -> {
                restoreFps(mc);
                log(dir, lineNo + " quit");
                LINES.clear();
                mc.stop();
                return;
            }
            default -> {
            }
        }
        String command = line.startsWith("/") ? line.substring(1) : line;
        String first = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(first)) {
            log(dir, lineNo + " refused (not allowed: " + first + "): " + line);
            return;
        }
        player.connection.sendCommand(command);
        log(dir, lineNo + " sent: /" + command);
    }

    private static void restoreFps(Minecraft mc) {
        if (savedFps >= 0) {
            mc.options.framerateLimit().set(savedFps);
            mc.options.enableVsync().set(savedVsync);
            savedFps = -1;
        }
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static void log(Path dir, String text) {
        String name = file != null ? file : "control";
        try {
            Path out = dir.resolve("outbox");
            Files.createDirectories(out);
            Files.writeString(out.resolve(name + ".log"), LocalDateTime.now().format(CLOCK) + " " + text
                    + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            BHFxMod.LOGGER.warn("bhfx dev control: could not log: {}", ex.getMessage());
        }
    }

    private static void writeStatus(Path dir, Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        sb.append("time: ").append(LocalDateTime.now()).append('\n');
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            sb.append("world: none (title screen or loading)\n");
        } else {
            sb.append("world: ").append(mc.level.dimension().location()).append('\n')
                    .append(String.format(Locale.ROOT, "player: %.2f %.2f %.2f yaw %.1f pitch %.1f%n",
                            p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()))
                    .append("game time: ").append(mc.level.getGameTime()).append(", day time: ")
                    .append(mc.level.getDayTime() % 24000).append('\n');
        }
        sb.append("camera: ").append(mc.options.getCameraType()).append('\n')
                .append("screen: ").append(mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()).append('\n')
                .append("window: ").append(mc.getWindow().getWidth()).append('x').append(mc.getWindow().getHeight())
                .append(", fps ").append(mc.getFps()).append('\n')
                .append("shoot busy: ").append(FxShots.busy()).append('\n')
                .append("inbox: ").append(file == null ? "idle" : file + " line " + lineNo + ", " + LINES.size() + " left")
                .append('\n');
        try {
            Path latest = FxShots.root().resolve("latest.txt");
            if (Files.exists(latest)) {
                sb.append("latest shots: ").append(FxShots.root().resolve(Files.readString(latest).trim())).append('\n');
            }
        } catch (IOException ignored) {
            // no shots yet
        }
        sb.append("bhfx: ").append(ClientFxManager.status().replace('\n', ' ')).append('\n');
        try {
            Path tmp = dir.resolve("status.txt.tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, dir.resolve("status.txt"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            // a reader held the file; next second tries again
        }
    }
}
