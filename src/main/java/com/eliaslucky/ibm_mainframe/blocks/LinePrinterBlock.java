package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.channel.ChannelNetworkManager;
import com.eliaslucky.ibm_mainframe.items.ListingItem;
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

public class LinePrinterBlock extends Block implements EntityBlock, ICustomCreativeTab {
	public LinePrinterBlock(Properties p) { super(p); }
	
	@Override
	public ResourceKey<CreativeModeTab> getCreativeTab() {
		return AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey();
	}

	@Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LinePrinterBlockEntity(pos, state);
	}

	@Override public void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean moved) {
		super.onPlace(s, l, p, o, moved);
		if (!l.isClientSide()) ChannelNetworkManager.invalidateLevel(l);
	}

	@Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean moved) {
		super.onRemove(s, l, p, n, moved);
		if (!l.isClientSide()) ChannelNetworkManager.invalidateLevel(l);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof LinePrinterBlockEntity be)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			if (!be.hasOutput()) {
				player.displayClientMessage(
						Component.literal("1403: no output pending."), true);
			} else {
				ItemStack listing = ListingItem.of(be.collectOutput());
				if (!player.getInventory().add(listing)) player.drop(listing, false);
				player.displayClientMessage(
						Component.literal("Listing collected."), true);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
