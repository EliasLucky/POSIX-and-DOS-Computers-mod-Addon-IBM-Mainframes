package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.channel.ChannelNetworkManager;
import com.eliaslucky.ibm_mainframe.machine.MainframeType;
import com.eliaslucky.mc_dos.blocks.computer.IBMComputerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The S/360 cabinet block. Everything else media, occupy, terminal
 * is inherited; the only thing that differs is the machine type,
 * whose {@code createBus} returns a channel bus.
 *
 * <p>Placement and removal invalidate the level's channel network
 * cache so a newly-attached or newly-detached CPU is seen on the next
 * scan. (Cables and channel devices do this on their own; the CPU is
 * not itself a cable, so it has to say so explicitly.)
 */
public class MainframeBlock extends IBMComputerBlock {
	public MainframeBlock(Properties properties) {
		super(properties, MainframeType.S360_MODEL_30);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		super.onPlace(state, level, pos, old, moved);
		if (!level.isClientSide()) ChannelNetworkManager.invalidateLevel(level);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
		super.onRemove(state, level, pos, next, moved);
		if (!level.isClientSide()) ChannelNetworkManager.invalidateLevel(level);
	}
}
