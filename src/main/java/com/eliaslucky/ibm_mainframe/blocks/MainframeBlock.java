package com.eliaslucky.ibm_mainframe.blocks;

import java.util.List;

import com.eliaslucky.ibm_mainframe.channel.ChannelNetworkManager;
import com.eliaslucky.ibm_mainframe.machine.MainframeKernel;
import com.eliaslucky.ibm_mainframe.machine.MainframeType;
import com.eliaslucky.mc_dos.api.hardware.Kernel;
import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;
import com.eliaslucky.mc_dos.blocks.computer.IBMComputerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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
	public MainframeBlock(Properties properties, MainframeType type) {
		super(properties, type);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		super.onPlace(state, level, pos, old, moved);
		if (!level.isClientSide()) {
			ChannelNetworkManager.invalidateLevel(level);
			for (Direction d : Direction.values()) {
				if (level.getBlockEntity(pos.relative(d)) instanceof ConsoleBlockEntity c) {
					c.tryBind();
				}
			}
		}
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
		super.onRemove(state, level, pos, next, moved);
		if (!level.isClientSide()) ChannelNetworkManager.invalidateLevel(level);
	}
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;

		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof ComputerBlockEntity cpu)) return InteractionResult.PASS;

		Kernel k = cpu.getKernel();
		if (!(k instanceof MainframeKernel mk)) {
			player.displayClientMessage(Component.literal(
					cpu.getMachineType().modelName() + " -- " + cpu.getBootState().name()),
					false);
			return InteractionResult.SUCCESS;
		}

		// Status line.
		player.displayClientMessage(Component.literal(mk.statusLine()), false);

		// Buffered operator log (up to the last 50 lines to avoid spam).
		List<String> pending = mk.peekOperatorBuffer();
		if (!pending.isEmpty()) {
			int start = Math.max(0, pending.size() - 50);
			player.displayClientMessage(Component.literal(
					"--- operator log (" + (pending.size() - start) + " of "
							+ pending.size() + " lines) ---"), false);
			for (int i = start; i < pending.size(); i++) {
				player.displayClientMessage(Component.literal(pending.get(i)), false);
			}
		}

		// Hint about what's missing.
		if (!mk.hasConsole()) {
			player.displayClientMessage(Component.literal(
					"No console attached. Place a console block adjacent to this CPU."),
					false);
		} else {
			player.displayClientMessage(Component.literal(
					"Console attached. Right-click the console to operate."),
					false);
		}

		return InteractionResult.SUCCESS;
	}
}
