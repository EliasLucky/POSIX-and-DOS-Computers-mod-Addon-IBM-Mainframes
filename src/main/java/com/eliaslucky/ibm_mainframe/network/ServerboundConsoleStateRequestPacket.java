package com.eliaslucky.ibm_mainframe.network;

import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundConsoleStateRequestPacket {
	private final BlockPos pos;

	public ServerboundConsoleStateRequestPacket(BlockPos pos) {
		this.pos = pos;
	}

	public ServerboundConsoleStateRequestPacket(FriendlyByteBuf buffer) {
		this.pos = buffer.readBlockPos();
	}

	public void encode(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(this.pos);
	}

	public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context ctx = contextSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player != null && player.level().getBlockEntity(this.pos) instanceof ConsoleBlockEntity computer) {
				ModMessages.sendToPlayer(new ClientboundConsoleOutputPacket(pos, c.getBuffer()), player);
			}
		});
		ctx.setPacketHandled(true);
	}
}
