package com.eliaslucky.mc_dos.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;

public class ServerboundConsoleCommandPacket {i
	public static final int MAX_CMD = 512;

	private final BlockPos pos;
	private final String command;

	public ServerboundConsoleCommandPacket(BlockPos pos, String command) {
		this.pos = pos;
		this.command = command;
	}

	public ServerboundConsoleCommandPacket(FriendlyByteBuf buffer) {
		this.pos = buffer.readBlockPos();
		this.command = buffer.readUtf(MAX_CMD);
	}

	public void encode(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(this.pos);
		buffer.writeUtf(this.command, MAX_CMD);
	}

	public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context ctx = contextSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null) return;

			ServerLevel level = player.serverLevel();
			BlockEntity be = level.getBlockEntity(this.pos);

			if (!(be instanceof ConsoleBlockEntity console)) return;

			if (!console.isBound()) {
				console.append("IEE100I NO PROCESSOR ATTACHED");
				return;
			}
			BlockEntity cpuBe = level.getBlockEntity(console.getBoundCpu());
			if (!(cpuBe instanceof ComputerBlockEntity cpu)) {
				console.append("IEE100I NO PROCESSOR ATTACHED");
				return;
			}
			console.append("IPL> " + command);
			String response = cpu.processCommand(command);
			if (response != null && !response.isEmpty()) {
				console.appendLines(List.of(response.split("\n",-1)));
			}
		});
		ctx.setPacketHandled(true);
	}
}
