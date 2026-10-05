package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.channel.ChannelNetworkManager;
import com.eliaslucky.ibm_mainframe.items.MagneticTapeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The IBM 2401 Magnetic Tape Unit. Single-reel drive. Right-click
 * with a reel to load; sneak-right-click empty-handed to unload.
 */
public class TapeDriveBlock extends Block implements EntityBlock {
	public TapeDriveBlock(Properties p) { super(p); }

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TapeDriveBlockEntity(pos, state);
	}

	@Override
	public void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean moved) {
		super.onPlace(s, l, p, o, moved);
		if (!l.isClientSide()) ChannelNetworkManager.invalidateLevel(l);
	}

	@Override
	public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean moved) {
		if (!l.isClientSide()
				&& !s.is(n.getBlock())
				&& l.getBlockEntity(p) instanceof TapeDriveBlockEntity be
				&& be.hasTape()) {
			ItemStack ejected = be.ejectTape();
			if (!ejected.isEmpty()) Block.popResource(l, p, ejected);
		}
		super.onRemove(s, l, p, n, moved);
		if (!l.isClientSide()) ChannelNetworkManager.invalidateLevel(l);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof TapeDriveBlockEntity be)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		boolean sneaking = player.isShiftKeyDown();

		if (sneaking && held.getItem() instanceof MagneticTapeItem) {
			if (!level.isClientSide()) {
				if (be.insertTape(held)) {
					if (!player.getAbilities().instabuild) held.shrink(1);
					player.displayClientMessage(
							Component.literal("Tape loaded."), true);
				} else {
					player.displayClientMessage(
							Component.literal("Drive already has a tape."), true);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		}

		if (sneaking && held.isEmpty() && be.hasTape()) {
			if (!level.isClientSide()) {
				ItemStack ejected = be.ejectTape();
				if (!ejected.isEmpty() && !player.getInventory().add(ejected)) {
					player.drop(ejected, false);
				}
				player.displayClientMessage(
						Component.literal("Tape unloaded."), true);
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		}

		if (!level.isClientSide()) {
			player.displayClientMessage(Component.literal(be.statusLine()), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
    	if (level.isClientSide()) return null;
    	return (lvl, pos, st, be) -> {
        	if (be instanceof TapeDriveBlockEntity t) t.tick();
    	};
	}
}
