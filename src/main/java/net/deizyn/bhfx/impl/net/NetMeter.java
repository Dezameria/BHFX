package net.deizyn.bhfx.impl.net;

import io.netty.buffer.Unpooled;
import net.deizyn.bhfx.BHFxMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /bhfx_play net: counts what bhfx sends to each player for a few seconds, then reports it. Off (no work at all)
 * unless a measurement is running. Sizes are the encoded message plus {@link #OVERHEAD}, before Minecraft's own
 * framing and compression. Server thread only.
 */
public final class NetMeter {
    /** Custom payload packet id, the channel name {@code bhfx:main} and the message index. */
    static final int OVERHEAD = 12;

    /** What one player received during the measurement. */
    static final class Count {
        final String name;
        long bytes;
        int packets;
        int starts;

        Count(String name) {
            this.name = name;
        }
    }

    private static final Map<UUID, Count> PER_PLAYER = new HashMap<>();
    private static int ticksLeft;
    private static int ticks;
    private static int droppedPerTick;
    private static int droppedPerCaster;
    @Nullable
    private static CommandSourceStack reportTo;

    private NetMeter() {
    }

    public static boolean running() {
        return ticksLeft > 0;
    }

    /** Start (or restart) a measurement of {@code seconds}; the report goes to {@code source} and the log. */
    public static void start(int seconds, @Nullable CommandSourceStack source) {
        PER_PLAYER.clear();
        ticks = 0;
        droppedPerTick = 0;
        droppedPerCaster = 0;
        ticksLeft = Math.max(1, seconds) * 20;
        reportTo = source;
    }

    public static void cancel() {
        ticksLeft = 0;
        PER_PLAYER.clear();
        reportTo = null;
    }

    static void toPlayer(ServerPlayer player, Object message) {
        if (running()) {
            add(player, sizeOf(message), starts(message));
        }
    }

    static void toDimension(ResourceKey<Level> dimension, Object message) {
        if (!running()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? server.getLevel(dimension) : null;
        if (level == null) {
            return;
        }
        int size = sizeOf(message);
        int starts = starts(message);
        for (ServerPlayer player : level.players()) {
            add(player, size, starts);
        }
    }

    /** A start the server's rate limits refused while a measurement runs. */
    public static void dropped(boolean perCaster) {
        if (running()) {
            if (perCaster) {
                droppedPerCaster++;
            } else {
                droppedPerTick++;
            }
        }
    }

    /** Once per server tick; reports when the time is up. */
    public static void tick(int runningEffects) {
        if (!running()) {
            return;
        }
        ticks++;
        if (--ticksLeft > 0) {
            return;
        }
        List<String> lines = report(new ArrayList<>(PER_PLAYER.values()), ticks, droppedPerTick, droppedPerCaster,
                runningEffects);
        CommandSourceStack source = reportTo;
        for (String line : lines) {
            BHFxMod.LOGGER.info(line);
            if (source != null) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        PER_PLAYER.clear();
        reportTo = null;
    }

    private static void add(ServerPlayer player, int bytes, int starts) {
        Count c = PER_PLAYER.computeIfAbsent(player.getUUID(), u -> new Count(player.getGameProfile().getName()));
        c.bytes += bytes;
        c.packets++;
        c.starts += starts;
    }

    static int starts(Object message) {
        if (message instanceof S2CFxStart) {
            return 1;
        }
        return message instanceof S2CFxStartBatch batch ? batch.starts().size() : 0;
    }

    /** Encoded size of a bhfx message plus {@link #OVERHEAD}; 0 for anything else. */
    static int sizeOf(Object message) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(64));
        try {
            if (message instanceof S2CFxStart m) {
                m.encode(buf);
            } else if (message instanceof S2CFxStartBatch m) {
                m.encode(buf);
            } else if (message instanceof S2CFxStop m) {
                m.encode(buf);
            } else if (message instanceof S2CFxUpdate m) {
                m.encode(buf);
            } else if (message instanceof S2CScreenPulse m) {
                m.encode(buf);
            } else {
                return 0;
            }
            return buf.writerIndex() + OVERHEAD;
        } finally {
            buf.release();
        }
    }

    /** The report lines; pure, for tests. */
    static List<String> report(List<Count> counts, int ticks, int droppedPerTick, int droppedPerCaster, int runningEffects) {
        double seconds = Math.max(1, ticks) / 20.0;
        List<String> lines = new ArrayList<>();
        lines.add(String.format("bhfx net over %.0f s (message + ~%d B per packet, before Minecraft framing and compression):",
                seconds, OVERHEAD));
        if (counts.isEmpty()) {
            lines.add("  nothing sent");
        } else {
            long bytes = 0;
            long packets = 0;
            long starts = 0;
            for (Count c : counts) {
                bytes += c.bytes;
                packets += c.packets;
                starts += c.starts;
            }
            lines.add(String.format("  total: %.1f KB/s, %.1f packets/s, %.1f starts/s, to %d player(s)",
                    bytes / 1024.0 / seconds, packets / seconds, starts / seconds, counts.size()));
            counts.sort(Comparator.comparingLong((Count c) -> c.bytes).reversed().thenComparing(c -> c.name));
            Count top = counts.get(0);
            lines.add(String.format("  per player: avg %.2f KB/s, max %.2f KB/s (%s)",
                    bytes / 1024.0 / seconds / counts.size(), top.bytes / 1024.0 / seconds, top.name));
            StringBuilder sb = new StringBuilder("  most:");
            for (int i = 0; i < Math.min(5, counts.size()); i++) {
                Count c = counts.get(i);
                sb.append(i == 0 ? " " : "; ").append(String.format("%s %.2f KB/s, %.1f starts/s",
                        c.name, c.bytes / 1024.0 / seconds, c.starts / seconds));
            }
            lines.add(sb.toString());
        }
        lines.add(String.format("  dropped by limits: %d by maxStartsPerTick, %d by maxActivePerCaster; %d effect(s) running now",
                droppedPerTick, droppedPerCaster, runningEffects));
        return lines;
    }
}
