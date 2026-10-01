package com.eliaslucky.ibm_mainframe.network;

import com.eliaslucky.ibm_mainframe.items.CardDeckItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client asks the server to punch a deck from text the player typed
 * at a keypunch. Server creates the deck item and gives it to the
 * player. Nothing about the text is trusted — length and per-card
 * column count are enforced server-side by {@link CardDeckItem#of}.
 */
public class ServerboundPunchDeckPacket {
	public static final int MAX_TEXT_LENGTH = 2000 * 80;
	public static final int MAX_LINES = 2000;

	private final BlockPos keypunchPos;
	private final String text;

	public ServerboundPunchDeckPacket(BlockPos pos, String text) {
		this.keypunchPos = pos;
		this.text = text == null ? "" : text;
	}

	public ServerboundPunchDeckPacket(FriendlyByteBuf buf) {
		this.keypunchPos = buf.readBlockPos();
		this.text = buf.readUtf(MAX_TEXT_LENGTH);
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeBlockPos(keypunchPos);
		buf.writeUtf(text, MAX_TEXT_LENGTH);
	}

	public void handle(Supplier<NetworkEvent.Context> supplier) {
		NetworkEvent.Context ctx = supplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null) return;

			// Reject if the block isn't actually a keypunch (basic sanity).
			if (!(player.serverLevel().getBlockState(keypunchPos).getBlock() instanceof KeypunchBlock)) {
				return;
			}

			List<String> cards = new ArrayList<>();
			String[] rawLines = text.split("\n", -1);
			for (int i = 0; i < rawLines.length && i < MAX_LINES; i++) {
				cards.add(rawLines[i]);
			}
			if (cards.isEmpty()) return;

			ItemStack deck = CardDeckItem.of(cards);
			if (!player.getInventory().add(deck)) player.drop(deck, false);
		});
		ctx.setPacketHandled(true);
	}
}
