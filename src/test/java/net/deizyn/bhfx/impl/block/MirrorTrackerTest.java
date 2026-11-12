package net.deizyn.bhfx.impl.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MirrorTrackerTest {
    @AfterEach
    void reset() {
        MirrorTracker.clear();
    }

    @Test
    void lonelyMirrorHasSixFaces() {
        BlockPos pos = new BlockPos(3, 10, 3);
        MirrorTracker.add(pos);
        assertEquals(6, MirrorTracker.clusters().size());
        for (Direction dir : Direction.values()) {
            assertTrue(MirrorTracker.hasFace(pos, dir));
        }
    }

    @Test
    void touchingFacesHideAndComeBack() {
        BlockPos a = new BlockPos(0, 64, 0);
        BlockPos b = a.east();
        MirrorTracker.add(a);
        MirrorTracker.add(b);
        assertFalse(MirrorTracker.hasFace(a, Direction.EAST));
        assertFalse(MirrorTracker.hasFace(b, Direction.WEST));
        assertTrue(MirrorTracker.hasFace(a, Direction.WEST));
        MirrorTracker.remove(b);
        assertTrue(MirrorTracker.hasFace(a, Direction.EAST));
        MirrorTracker.remove(a);
        assertTrue(MirrorTracker.isEmpty());
        assertTrue(MirrorTracker.clusters().isEmpty());
    }

    @Test
    void faceSitsOnTheFacingSide() {
        MirrorTracker.add(new BlockPos(3, 10, 3));
        for (MirrorTracker.Cluster cluster : MirrorTracker.clusters()) {
            if (cluster.key.facing() == Direction.UP) {
                assertEquals(11.0, cluster.faceAt);
            } else if (cluster.key.facing() == Direction.NORTH) {
                assertEquals(3.0, cluster.faceAt);
            }
        }
    }

    @Test
    void wallMergesIntoOneRectanglePerTile() {
        // 20 wide, 3 high wall at z = 5: north faces split into tiles x 0..15 and 16..19, one rectangle each
        for (int x = 0; x < 20; x++) {
            for (int y = 64; y < 67; y++) {
                MirrorTracker.add(new BlockPos(x, y, 5));
            }
        }
        List<int[]> north = new ArrayList<>();
        for (MirrorTracker.Cluster cluster : MirrorTracker.clusters()) {
            if (cluster.key.facing() == Direction.NORTH) {
                north.add(cluster.rects());
            }
        }
        assertEquals(2, north.size());
        north.sort((p, q) -> Integer.compare(p[0], q[0]));
        assertArrayEquals(new int[] {0, 64, 16, 67}, north.get(0)); // u = x, v = y for Z faces
        assertArrayEquals(new int[] {16, 64, 20, 67}, north.get(1));
    }

    @Test
    void rectanglesFollowRemovals() {
        for (int x = 0; x < 3; x++) {
            MirrorTracker.add(new BlockPos(x, 64, 0));
        }
        MirrorTracker.remove(new BlockPos(1, 64, 0));
        for (MirrorTracker.Cluster cluster : MirrorTracker.clusters()) {
            if (cluster.key.facing() == Direction.UP) {
                assertEquals(8, cluster.rects().length); // two separate 1x1 rectangles
            }
        }
    }

    @Test
    void boundsFollowRemovals() {
        MirrorTracker.add(new BlockPos(0, 64, 0));
        MirrorTracker.add(new BlockPos(5, 64, 0));
        MirrorTracker.remove(new BlockPos(5, 64, 0));
        for (MirrorTracker.Cluster cluster : MirrorTracker.clusters()) {
            assertEquals(1.0, cluster.bounds().maxX);
        }
    }

    @Test
    void nearbyLookupSkipsFarSections() {
        MirrorTracker.add(new BlockPos(0, 64, 0));
        MirrorTracker.add(new BlockPos(1000, 64, 1000));
        List<MirrorTracker.Cluster> near = new ArrayList<>();
        MirrorTracker.forEachNear(new Vec3(0, 64, 0), 32, near::add);
        assertEquals(6, near.size());
    }

    @Test
    void panelReflectsOnlyItsFrontJustOffTheWall() {
        BlockPos east = new BlockPos(3, 64, 3);
        BlockPos west = new BlockPos(8, 64, 3);
        MirrorTracker.addPanel(east, Direction.EAST); // laid on a wall at x = 2
        MirrorTracker.addPanel(west, Direction.WEST); // laid on a wall at x = 9
        assertEquals(2, MirrorTracker.clusters().size());
        assertTrue(MirrorTracker.hasFace(east, Direction.EAST, true));
        assertFalse(MirrorTracker.hasFace(east, Direction.EAST)); // not a block face
        assertFalse(MirrorTracker.hasFace(east, Direction.WEST, true));
        for (MirrorTracker.Cluster cluster : MirrorTracker.clusters()) {
            double expected = cluster.key.facing() == Direction.EAST ? 3 + 1 / 16.0 : 9 - 1 / 16.0;
            assertEquals(expected, cluster.faceAt, 1e-9);
        }
    }

    @Test
    void panelsOnOneWallMergeIntoOneSurface() {
        for (int x = 0; x < 4; x++) {
            for (int y = 64; y < 67; y++) {
                MirrorTracker.addPanel(new BlockPos(x, y, 5), Direction.NORTH);
            }
        }
        assertEquals(1, MirrorTracker.clusters().size());
        MirrorTracker.Cluster cluster = MirrorTracker.clusters().iterator().next();
        assertArrayEquals(new int[] {0, 64, 4, 67}, cluster.rects());
    }

    @Test
    void panelsAndBlocksOfOneLayerAreSeparateSurfaces() {
        MirrorTracker.add(new BlockPos(0, 64, 5));
        MirrorTracker.addPanel(new BlockPos(1, 64, 5), Direction.NORTH);
        long blockPlane = 0;
        long panelPlane = 0;
        for (MirrorTracker.Cluster cluster : MirrorTracker.clusters()) {
            if (cluster.key.facing() == Direction.NORTH) {
                if (cluster.key.panel()) {
                    panelPlane = cluster.key.planeId();
                } else {
                    blockPlane = cluster.key.planeId();
                }
            }
        }
        assertTrue(blockPlane != panelPlane);
        // a panel beside a block hides none of the block's faces
        assertTrue(MirrorTracker.hasFace(new BlockPos(0, 64, 5), Direction.EAST));
    }

    @Test
    void turnedOrRemovedPanelLeavesNothingBehind() {
        BlockPos pos = new BlockPos(0, 64, 0);
        MirrorTracker.addPanel(pos, Direction.NORTH);
        MirrorTracker.addPanel(pos, Direction.UP);
        assertFalse(MirrorTracker.hasFace(pos, Direction.NORTH, true));
        assertTrue(MirrorTracker.hasFace(pos, Direction.UP, true));
        assertEquals(1, MirrorTracker.mirrorCount());
        MirrorTracker.remove(pos);
        assertTrue(MirrorTracker.isEmpty());
        assertTrue(MirrorTracker.clusters().isEmpty());
    }

    @Test
    void negativeCoordinatesStayInTheirTile() {
        MirrorTracker.add(new BlockPos(-1, 64, 0));
        MirrorTracker.add(new BlockPos(-1, 64, 5));
        // both north faces at different z layers; each in tile u = -1
        for (MirrorTracker.Cluster cluster : MirrorTracker.clusters()) {
            if (cluster.key.facing() == Direction.NORTH) {
                assertEquals(-1, cluster.key.tileU());
            }
        }
    }
}
