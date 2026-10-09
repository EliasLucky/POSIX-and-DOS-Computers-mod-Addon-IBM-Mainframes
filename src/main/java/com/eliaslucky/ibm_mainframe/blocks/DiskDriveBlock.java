package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.channel.ChannelNetworkManager;
import com.eliaslucky.ibm_mainframe.items.DiskPackItem;
import com.eliaslucky.ibm_mainframe.AllCreativeModeTabs;
import com.eliaslucky.mc_dos.blocks.ICustomCreativeTab;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The IBM 2311 Disk Storage Drive. Accepts 1316 disk packs. */
public class DiskDriveBlock extends Block implements EntityBlock, ICustomCreativeTab {
	public DiskDriveBlock(Properties p) { super(p); }
	
	@Override
	public ResourceKey<CreativeModeTab> getCreativeTab() {
		return AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DiskDriveBlockEntity(pos, state);
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
				&& l.getBlockEntity(p) instanceof DiskDriveBlockEntity be
				&& be.hasPack()) {
			ItemStack ejected = be.ejectPack();
			if (!ejected.isEmpty()) Block.popResource(l, p, ejected);
		}
		super.onRemove(s, l, p, n, moved);
		if (!l.isClientSide()) ChannelNetworkManager.invalidateLevel(l);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof DiskDriveBlockEntity be)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		boolean sneaking = player.isShiftKeyDown();

		if (sneaking && held.getItem() instanceof DiskPackItem) {
			if (!level.isClientSide()) {
				if (be.insertPack(held)) {
					if (!player.getAbilities().instabuild) held.shrink(1);
					player.displayClientMessage(
							Component.literal("Pack mounted: "
									+ be.volumeSerial()), true);
				} else {
					player.displayClientMessage(
							Component.literal("Drive already has a pack."), true);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		}

		if (sneaking && held.isEmpty() && be.hasPack()) {
			if (!level.isClientSide()) {
				ItemStack ejected = be.ejectPack();
				if (!ejected.isEmpty() && !player.getInventory().add(ejected)) {
					player.drop(ejected, false);
				}
				player.displayClientMessage(
						Component.literal("Pack dismounted."), true);
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		}

		if (!level.isClientSide()) {
			player.displayClientMessage(Component.literal(be.statusLine()), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
