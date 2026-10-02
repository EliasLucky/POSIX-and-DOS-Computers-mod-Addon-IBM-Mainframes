package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.channel.ChannelNetworkManager;
import com.eliaslucky.ibm_mainframe.machine.MainframeKernel;
import com.eliaslucky.ibm_mainframe.machine.MainframeType;
import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;
import com.eliaslucky.mc_dos.blocks.computer.IBMComputerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
	    if (!level.isClientSide()) {
	        BlockEntity be = level.getBlockEntity(pos);
	        if (be instanceof ComputerBlockEntity cpu) {
	            StringBuilder sb = new StringBuilder();
	            sb.append(cpu.getMachineType().modelName());
	            sb.append(" -- ");
	            sb.append(cpu.getBootState().name());

	            if (cpu.getKernel() instanceof MainframeKernel k) {
	                sb.append("  Devices: ").append(k.deviceTable().all().size());
	                sb.append("  Console: ")
	                  .append(k.hasConsole() ? "attached" : "NOT ATTACHED");
	            }
	            player.displayClientMessage(Component.literal(sb.toString()), true);
	        }
	    }
	    return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
