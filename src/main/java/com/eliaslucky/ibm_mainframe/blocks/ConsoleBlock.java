package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.client.ConsoleScreen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The IBM 1052 Console Printer-Keyboard.
 *
 * <p>Not a channel device. Wired to a dedicated console interface on
 * the CPU. Must be placed orthogonally adjacent to a mainframe
 * cabinet; the cabinet discovers it on boot and binds it as its
 * operator console.
 *
 * <p>Right-click opens the console screen. If no CPU is bound,
 * the screen shows a diagnostic and refuses input.
 */
public class ConsoleBlock extends Block implements EntityBlock {
	public ConsoleBlock(Properties p) { super(p); }

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

		// Try to bind to an adjacent CPU right away.
		BlockEntity be = l.getBlockEntity(p);
		if (be instanceof ConsoleBlockEntity console) {
			console.tryBind();
		}
	}

	@Override
	public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean moved) {
		if (!l.isClientSide() && !s.is(n.getBlock()) && l.getBlockEntity(p) instanceof ConsoleBlockEntity console) {
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
			net.minecraft.client.Minecraft.getInstance().setScreen(new ConsoleScreen(pos));
		}
		else {
			// Re-check binding on every use a CPU may have been placed
			// after the console, or a CPU replaced.
			console.tryBind();
			if (!console.isBound()) {
				player.displayClientMessage(Component.literal("Console is not connected to a processor."), true);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
