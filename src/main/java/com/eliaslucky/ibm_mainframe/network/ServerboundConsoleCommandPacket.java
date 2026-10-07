package com.eliaslucky.ibm_mainframe.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

import com.eliaslucky.ibm_mainframe.blocks.ConsoleBlockEntity;
import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;

public class ServerboundConsoleCommandPacket {
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

			if (!(player.serverLevel().getBlockEntity(pos) instanceof ConsoleBlockEntity console)) {
				return;
			}

			if (!console.isBound()) {
				console.append("IEE100I NO PROCESSOR ATTACHED -- RIGHT-CLICK THE MAINFRAME TO IPL");
				ModMessages.sendToPlayer(
						ClientboundConsoleOutputPacket.replace(pos, console.getBuffer()),
						player);
				return;
			}

			var cpuBe = player.serverLevel().getBlockEntity(console.getBoundCpu());
			if (!(cpuBe instanceof ComputerBlockEntity cpu)) {
				console.append("IEE100I PROCESSOR GONE");
				ModMessages.sendToPlayer(
						ClientboundConsoleOutputPacket.replace(pos, console.getBuffer()),
						player);
				return;
			}

			console.append("IPL> " + command);

			// Process. If the kernel isn't ready yet, boot the CPU first.
			if (cpu.getKernel() == null) {
				cpu.powerOn();
				console.append("IEA000I IPL STARTED");
			}

			String response = cpu.processCommand(command);
			if (response != null && !response.isEmpty()) {
				for (String line : response.split("\n", -1)) console.append(line);
			}

			ModMessages.sendToPlayer(
					ClientboundConsoleOutputPacket.replace(pos, console.getBuffer()),
					player);
		});
		ctx.setPacketHandled(true);
	}
}
