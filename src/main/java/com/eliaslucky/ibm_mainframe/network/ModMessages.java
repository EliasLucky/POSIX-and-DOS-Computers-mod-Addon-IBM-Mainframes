package com.eliaslucky.ibm_mainframe.network;

import com.eliaslucky.ibm_mainframe.MainframeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
	private static SimpleChannel INSTANCE;
	private static int packetId = 0;

	private static int id() {
		return packetId++;
	}

	public static void register() {
		SimpleChannel net = NetworkRegistry.ChannelBuilder
				.named(ResourceLocation.fromNamespaceAndPath(MainframeMod.MODID, "messages"))
				.networkProtocolVersion(() -> "1.0")
				.clientAcceptedVersions(s -> true)
				.serverAcceptedVersions(s -> true)
				.simpleChannel();

		INSTANCE = net;

		// CLIENT -> SERVER
		net.messageBuilder(ServerboundConsoleCommandPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
				.decoder(ServerboundConsoleCommandPacket::new)
				.encoder(ServerboundConsoleCommandPacket::encode)
				.consumerMainThread(ServerboundConsoleCommandPacket::handle)
				.add();

		net.messageBuilder(ServerboundConsoleStateRequestPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
				.decoder(ServerboundConsoleStateRequestPacket::new)
				.encoder(ServerboundConsoleStateRequestPacket::encode)
				.consumerMainThread(ServerboundConsoleStateRequestPacket::handle)
				.add();

		net.messageBuilder(ServerboundPunchDeckPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
				.decoder(ServerboundPunchDeckPacket::new)
				.encoder(ServerboundPunchDeckPacket::encode)
				.consumerMainThread(ServerboundPunchDeckPacket::handle)
				.add();
				
		// SERVER -> CLIENT
		net.messageBuilder(ClientboundConsoleOutputPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
			.decoder(ClientboundConsoleOutputPacket::new)
			.encoder(ClientboundConsoleOutputPacket::encode)
			.consumerMainThread(ClientboundConsoleOutputPacket::handle)
			.add();	
	}

	public static void sendToServer(Object message) {
		INSTANCE.sendToServer(message);
	}

	public static void sendToPlayer(Object message, ServerPlayer player) {
		INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
	}
}
