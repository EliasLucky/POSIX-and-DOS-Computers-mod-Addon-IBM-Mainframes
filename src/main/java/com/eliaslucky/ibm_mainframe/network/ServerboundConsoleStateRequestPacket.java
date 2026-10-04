package com.eliaslucky.ibm_mainframe.network;

import com.eliaslucky.ibm_mainframe.blocks.ConsoleBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asks the server for the full current buffer of a console.
 * Sent once when the console screen opens.
 *
 * <p>The server replies with a {@link ClientboundConsoleOutputPacket}
 * in {@code replace} mode, so the client's local copy matches the
 * authoritative server-side buffer exactly.
 */
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
			if (player == null) return;

			if (player.serverLevel().getBlockEntity(this.pos)
					instanceof ConsoleBlockEntity console) {
				ModMessages.sendToPlayer(
						ClientboundConsoleOutputPacket.replace(this.pos, console.getBuffer()),
						player);
			} else {
				ModMessages.sendToPlayer(
						ClientboundConsoleOutputPacket.replace(this.pos, java.util.List.of()),
						player);
			}
		});
		ctx.setPacketHandled(true);
	}
}
