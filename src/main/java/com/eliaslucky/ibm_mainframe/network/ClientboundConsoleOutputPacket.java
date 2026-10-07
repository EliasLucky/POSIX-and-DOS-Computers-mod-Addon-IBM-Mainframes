package com.eliaslucky.ibm_mainframe.network;

import com.eliaslucky.ibm_mainframe.client.ConsoleScreenBase;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server pushes lines to an open console screen.
 *
 * <p>Two modes:
 * <ul>
 *	 <li>{@link Mode#REPLACE} the client replaces its buffer with the
 *		 incoming list. Used for the initial sync when the screen
 *		 opens.</li>
 *	 <li>{@link Mode#APPEND} the client appends the incoming lines.
 *		 Used for live output while the screen is open.</li>
 * </ul>
 *
 * <p>Server-side safety: the line list is capped at
 * {@link #MAX_LINES_PER_PACKET} and each line at {@link #MAX_LINE_LEN}
 * before encoding, so a runaway program can't create an oversized
 * packet.
 */
public class ClientboundConsoleOutputPacket {
	public enum Mode { REPLACE, APPEND }

	public static final int MAX_LINES_PER_PACKET = 512;
	public static final int MAX_LINE_LEN		 = 512;

	private final BlockPos pos;
	private final Mode mode;
	private final List<String> lines;

	public ClientboundConsoleOutputPacket(BlockPos pos, Mode mode, List<String> lines) {
		this.pos = pos;
		this.mode = mode;
		this.lines = clamp(lines);
	}

	/** Convenience: a replace-mode packet. */
	public static ClientboundConsoleOutputPacket replace(BlockPos pos, List<String> lines) {
		return new ClientboundConsoleOutputPacket(pos, Mode.REPLACE, lines);
	}

	/** Convenience: an append-mode packet. */
	public static ClientboundConsoleOutputPacket append(BlockPos pos, List<String> lines) {
		return new ClientboundConsoleOutputPacket(pos, Mode.APPEND, lines);
	}

	/** Convenience: a single-line append. */
	public static ClientboundConsoleOutputPacket append(BlockPos pos, String line) {
		return new ClientboundConsoleOutputPacket(pos, Mode.APPEND, List.of(line));
	}

	public ClientboundConsoleOutputPacket(FriendlyByteBuf buffer) {
		this.pos = buffer.readBlockPos();
		this.mode = Mode.values()[buffer.readByte()];
		int n = buffer.readVarInt();
		List<String> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) list.add(buffer.readUtf(MAX_LINE_LEN));
		this.lines = list;
	}

	public void encode(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeByte(mode.ordinal());
		buffer.writeVarInt(lines.size());
		for (String l : lines) buffer.writeUtf(l, MAX_LINE_LEN);
	}

	public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context ctx = contextSupplier.get();
		ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			if (Minecraft.getInstance().screen instanceof ConsoleScreenBase cs) {
				if (mode == Mode.REPLACE) {
					cs.setBuffer(lines);
				} else {
					for (String l : lines) cs.appendLine(l);
				}
			}
		}));
		ctx.setPacketHandled(true);
	}

	private static List<String> clamp(List<String> in) {
		if (in == null) return List.of();
		int n = Math.min(in.size(), MAX_LINES_PER_PACKET);
		List<String> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			String s = in.get(i);
			if (s == null) s = "";
			if (s.length() > MAX_LINE_LEN) s = s.substring(0, MAX_LINE_LEN);
			out.add(s);
		}
		return out;
	}
}
