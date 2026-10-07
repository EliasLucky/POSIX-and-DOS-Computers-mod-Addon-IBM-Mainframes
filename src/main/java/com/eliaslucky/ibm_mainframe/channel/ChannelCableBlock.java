package com.eliaslucky.ibm_mainframe.channel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A channel cable segment. Cables connect to each other horizontally
 * only; a vertical stack of cables is two separate buses. Devices
 * attach to a cable by being orthogonally adjacent to it (typically
 * by sitting on top).
 *
 * <p>No block entity. The connection state is fully derivable from
 * neighbours, exactly like redstone dust or a fence.
 */
public class ChannelCableBlock extends Block {
	public ChannelCableBlock(Properties properties) {
		super(properties);
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

	/** True if this state is a cable block. Convenience for scanning. */
	public static boolean isCable(BlockState s) {
		return s.getBlock() instanceof ChannelCableBlock;
	}
}
