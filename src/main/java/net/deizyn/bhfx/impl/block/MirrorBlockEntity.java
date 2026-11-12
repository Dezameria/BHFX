package net.deizyn.bhfx.impl.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds no data; it only tells the client renderer which mirrors (blocks and panels) are loaded. Shared by
 * {@link MirrorBlock} and {@link MirrorPanelBlock}.
 */
public final class MirrorBlockEntity extends BlockEntity {
    public MirrorBlockEntity(BlockPos pos, BlockState state) {
        super(BHFxBlocks.MIRROR_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            track(getBlockState());
        }
    }

    /** A panel turned in place (debug stick, structure tools) keeps its block entity; file it again. */
    @SuppressWarnings("deprecation")
    @Override
    public void setBlockState(BlockState state) {
        boolean turned = state != getBlockState();
        super.setBlockState(state);
        if (turned && level != null && level.isClientSide && state.getBlock() instanceof MirrorPanelBlock) {
            MirrorTracker.remove(worldPosition);
            track(state);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            MirrorTracker.remove(worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            MirrorTracker.remove(worldPosition);
        }
    }

    private void track(BlockState state) {
        if (state.getBlock() instanceof MirrorPanelBlock) {
            MirrorTracker.addPanel(worldPosition, state.getValue(MirrorPanelBlock.FACING));
        } else {
            MirrorTracker.add(worldPosition);
        }
    }
}
