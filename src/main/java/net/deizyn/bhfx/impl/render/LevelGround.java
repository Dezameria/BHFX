package net.deizyn.bhfx.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** {@link net.deizyn.bhfx.api.client.FxEmitter.Shape#onGround} against the client world. Render thread. */
final class LevelGround {
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    private LevelGround() {
    }

    /**
     * The top of the surface nearest the point (blocks and fluid surfaces): if the point is inside a block or fluid,
     * the top of the column it is in (searching up); otherwise the top of the first block or fluid below it. NaN when none is within
     * {@code search} blocks, or no world.
     */
    static double surface(double x, double y, double z, double search) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Double.NaN;
        }
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int by = Mth.floor(y);
        double inside = top(level, bx, by, bz);
        if (!Double.isNaN(inside) && inside > y) {
            // buried: climb to the first open space
            int limit = Mth.floor(y + search);
            double surface = inside;
            for (int cy = by + 1; cy <= limit; cy++) {
                double t = top(level, bx, cy, bz);
                if (Double.isNaN(t)) {
                    return surface;
                }
                surface = t;
            }
            return Double.NaN;
        }
        if (!Double.isNaN(inside)) {
            return inside; // standing on a part block (slab, path) in this cell, below the point
        }
        int limit = Mth.floor(y - search);
        for (int cy = by - 1; cy >= limit; cy--) {
            double t = top(level, bx, cy, bz);
            if (!Double.isNaN(t)) {
                return t;
            }
        }
        return Double.NaN;
    }

    /**
     * World y of the top of what is in the cell: the block's collision shape or its fluid surface (water, lava, a
     * waterlogged block), whichever is higher; NaN if neither. Fluids count because the world's depth hides anything
     * drawn under a water surface.
     */
    private static double top(ClientLevel level, int x, int y, int z) {
        POS.set(x, y, z);
        BlockState state = level.getBlockState(POS);
        if (state.isAir()) {
            return Double.NaN;
        }
        double top = Double.NaN;
        VoxelShape shape = state.getCollisionShape(level, POS);
        if (!shape.isEmpty()) {
            top = y + shape.max(Direction.Axis.Y);
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            double surface = y + fluid.getHeight(level, POS);
            top = Double.isNaN(top) ? surface : Math.max(top, surface);
        }
        return top;
    }
}
