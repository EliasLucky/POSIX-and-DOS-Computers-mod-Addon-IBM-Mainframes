package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.client.KeypunchScreen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class KeypunchBlock extends Block {
	public KeypunchBlock(Properties p) { super(p); }

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			// Open the punch UI. The UI is a client screen that, on
			// "Punch", sends ServerboundPunchDeckPacket with the buffer.
			net.minecraft.client.Minecraft.getInstance().setScreen(new KeypunchScreen(pos));
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
