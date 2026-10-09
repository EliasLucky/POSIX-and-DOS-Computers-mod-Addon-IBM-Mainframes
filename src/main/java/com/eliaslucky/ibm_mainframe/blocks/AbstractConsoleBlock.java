package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.mc_dos.AllCreativeModeTabs;
import com.eliaslucky.mc_dos.blocks.ICustomCreativeTab;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared behaviour for every console block in the mod.
 *
 * <p>Consoles are not channel devices. They attach to a dedicated
 * console interface on a mainframe cabinet. One console per machine;
 * a second console displaces the first.
 *
 * <p>Subclasses supply exactly one thing: the screen to open on the
 * client when the block is right-clicked. Everything else — binding,
 * tick, placement, removal — lives here.
 *
 * <p>See {@link Console1052Block} and {@link Console3270Block}.
 */
public abstract class AbstractConsoleBlock extends Block implements EntityBlock, ICustomCreativeTab {
	protected AbstractConsoleBlock(Properties p) { super(p); }
	
	@Override
	public ResourceKey<CreativeModeTab> getCreativeTab() {
		return AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ConsoleBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide()) return null;
		return (lvl, pos, st, be) -> {
			if (be instanceof ConsoleBlockEntity console) console.tick();
		};
	}

	@Override
	public void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean moved) {
		super.onPlace(s, l, p, o, moved);
		if (l.isClientSide()) return;
		if (l.getBlockEntity(p) instanceof ConsoleBlockEntity console) {
			console.tryBind();
		}
	}

	@Override
	public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean moved) {
		if (!l.isClientSide()
				&& !s.is(n.getBlock())
				&& l.getBlockEntity(p) instanceof ConsoleBlockEntity console) {
			console.unbind();
		}
		super.onRemove(s, l, p, n, moved);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof ConsoleBlockEntity console)) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			openScreenClient(pos);
		} else {
			console.tryBind();
			if (!console.isBound()) {
				player.displayClientMessage(
						Component.literal("Console is not connected to a processor."),
						true);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	/**
	 * Open the client-side screen for this console model. Called only
	 * on the client, only from {@link #use}.
	 *
	 * @param pos the console's block position
	 */
	protected abstract void openScreenClient(BlockPos pos);
}
