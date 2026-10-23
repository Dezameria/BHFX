package net.deizyn.bhfx.impl.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Mirror faces loaded in the client level. Every face of a mirror block reflects, except one that touches another
 * mirror. Faces are kept in clusters (facing, block layer along the facing axis, 16x16 tile of that plane), each with
 * its faces merged into rectangles, and clusters are indexed by chunk section, so the renderer only looks at the
 * sections around the camera: a maze of tens of thousands of mirrors costs about what the nearby ones cost.
 * Lives in common code so {@link MirrorBlockEntity} can reach it without loading client classes; only the client
 * thread uses it.
 */
public final class MirrorTracker {
    private static final Set<BlockPos> MIRRORS = new HashSet<>();
    private static final Map<Key, Cluster> CLUSTERS = new HashMap<>();
    private static final Map<Long, List<Cluster>> BY_SECTION = new HashMap<>();

    private MirrorTracker() {
    }

    static void add(BlockPos pos) {
        BlockPos at = pos.immutable();
        if (!MIRRORS.add(at)) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockPos next = at.relative(dir);
            if (MIRRORS.contains(next)) {
                removeFace(next, dir.getOpposite()); // the two faces now touch
            } else {
                addFace(at, dir);
            }
        }
    }

    static void remove(BlockPos pos) {
        if (!MIRRORS.remove(pos)) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            if (MIRRORS.contains(next)) {
                addFace(next, dir.getOpposite()); // uncovered again
            } else {
                removeFace(pos, dir);
            }
        }
    }

    private static void addFace(BlockPos pos, Direction facing) {
        Key key = Key.of(pos, facing);
        Cluster cluster = CLUSTERS.get(key);
        if (cluster == null) {
            cluster = new Cluster(key);
            CLUSTERS.put(key, cluster);
            BY_SECTION.computeIfAbsent(key.section(), s -> new ArrayList<>()).add(cluster);
        }
        cluster.add(pos);
    }

    private static void removeFace(BlockPos pos, Direction facing) {
        Key key = Key.of(pos, facing);
        Cluster cluster = CLUSTERS.get(key);
        if (cluster != null && cluster.remove(pos)) {
            CLUSTERS.remove(key);
            List<Cluster> inSection = BY_SECTION.get(key.section());
            if (inSection != null) {
                inSection.remove(cluster);
                if (inSection.isEmpty()) {
                    BY_SECTION.remove(key.section());
                }
            }
        }
    }

    /** Whether the mirror at {@code pos} has a reflecting face toward {@code facing}. */
    public static boolean hasFace(BlockPos pos, Direction facing) {
        Cluster cluster = CLUSTERS.get(Key.of(pos, facing));
        return cluster != null && cluster.blocks.contains(pos);
    }

    /** Calls {@code action} for every cluster in the chunk sections within {@code range} blocks of {@code centre}. */
    public static void forEachNear(Vec3 centre, double range, Consumer<Cluster> action) {
        int x0 = SectionPos.blockToSectionCoord(centre.x - range), x1 = SectionPos.blockToSectionCoord(centre.x + range);
        int y0 = SectionPos.blockToSectionCoord(centre.y - range), y1 = SectionPos.blockToSectionCoord(centre.y + range);
        int z0 = SectionPos.blockToSectionCoord(centre.z - range), z1 = SectionPos.blockToSectionCoord(centre.z + range);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    List<Cluster> clusters = BY_SECTION.get(SectionPos.asLong(x, y, z));
                    if (clusters != null) {
                        clusters.forEach(action);
                    }
                }
            }
        }
    }

    public static boolean isEmpty() {
        return MIRRORS.isEmpty();
    }

    public static int mirrorCount() {
        return MIRRORS.size();
    }

    public static Collection<Cluster> clusters() {
        return CLUSTERS.values();
    }

    public static void clear() {
        MIRRORS.clear();
        CLUSTERS.clear();
        BY_SECTION.clear();
    }

    /** facing + the block layer along the facing axis + which 16x16 tile of the plane. */
    public record Key(Direction facing, int layer, int tileU, int tileV) {
        static Key of(BlockPos pos, Direction facing) {
            Direction.Axis axis = facing.getAxis();
            return new Key(facing, pos.get(axis), u(pos, axis) >> 4, v(pos, axis) >> 4);
        }

        /** Same for every cluster of one flat mirror surface. */
        public long planeId() {
            return ((long) facing.ordinal() << 32) | (layer & 0xFFFFFFFFL);
        }

        /** The chunk section the cluster lies in (a tile is 16x16 and one block thick). */
        long section() {
            int layerSection = SectionPos.blockToSectionCoord(layer);
            return switch (facing.getAxis()) {
                case X -> SectionPos.asLong(layerSection, tileU, tileV);
                case Y -> SectionPos.asLong(tileU, layerSection, tileV);
                case Z -> SectionPos.asLong(tileU, tileV, layerSection);
            };
        }
    }

    /** First in-plane coordinate of a block for faces along {@code axis}: Y for X faces, else X. */
    public static int u(BlockPos pos, Direction.Axis axis) {
        return axis == Direction.Axis.X ? pos.getY() : pos.getX();
    }

    /** Second in-plane coordinate: Y for Z faces, else Z. */
    public static int v(BlockPos pos, Direction.Axis axis) {
        return axis == Direction.Axis.Z ? pos.getY() : pos.getZ();
    }

    public static final class Cluster {
        public final Key key;
        /** World coordinate of the faces along the facing axis. */
        public final double faceAt;
        private final Set<BlockPos> blocks = new HashSet<>();
        @Nullable
        private AABB bounds;
        @Nullable
        private int[] rects;

        Cluster(Key key) {
            this.key = key;
            this.faceAt = key.facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? key.layer + 1 : key.layer;
        }

        void add(BlockPos pos) {
            if (blocks.add(pos)) {
                bounds = null;
                rects = null;
            }
        }

        /** True when the cluster is now empty. */
        boolean remove(BlockPos pos) {
            if (blocks.remove(pos)) {
                bounds = null;
                rects = null;
            }
            return blocks.isEmpty();
        }

        public Set<BlockPos> blocks() {
            return blocks;
        }

        /** Box around the cluster's blocks; rebuilt lazily, like {@link #rects()}, so chunk loads stay cheap. */
        public AABB bounds() {
            if (bounds == null) {
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                for (BlockPos pos : blocks) {
                    minX = Math.min(minX, pos.getX());
                    minY = Math.min(minY, pos.getY());
                    minZ = Math.min(minZ, pos.getZ());
                    maxX = Math.max(maxX, pos.getX());
                    maxY = Math.max(maxY, pos.getY());
                    maxZ = Math.max(maxZ, pos.getZ());
                }
                bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
            }
            return bounds;
        }

        /**
         * The faces merged into rectangles, as {@code u0, v0, u1, v1} quadruples in world block coordinates of the
         * plane (see {@link #u} / {@link #v}); greedy, rows first. Rebuilt only after the cluster changes.
         */
        public int[] rects() {
            if (rects == null) {
                rects = merge();
            }
            return rects;
        }

        private int[] merge() {
            Direction.Axis axis = key.facing.getAxis();
            int baseU = key.tileU << 4;
            int baseV = key.tileV << 4;
            boolean[] filled = new boolean[256];
            for (BlockPos pos : blocks) {
                filled[(u(pos, axis) - baseU) * 16 + (v(pos, axis) - baseV)] = true;
            }
            List<Integer> out = new ArrayList<>();
            for (int u = 0; u < 16; u++) {
                for (int v = 0; v < 16; v++) {
                    if (!filled[u * 16 + v]) {
                        continue;
                    }
                    int v1 = v;
                    while (v1 + 1 < 16 && filled[u * 16 + v1 + 1]) {
                        v1++;
                    }
                    int u1 = u;
                    grow:
                    while (u1 + 1 < 16) {
                        for (int k = v; k <= v1; k++) {
                            if (!filled[(u1 + 1) * 16 + k]) {
                                break grow;
                            }
                        }
                        u1++;
                    }
                    for (int a = u; a <= u1; a++) {
                        for (int k = v; k <= v1; k++) {
                            filled[a * 16 + k] = false;
                        }
                    }
                    out.add(baseU + u);
                    out.add(baseV + v);
                    out.add(baseU + u1 + 1);
                    out.add(baseV + v1 + 1);
                }
            }
            int[] result = new int[out.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = out.get(i);
            }
            return result;
        }
    }
}
