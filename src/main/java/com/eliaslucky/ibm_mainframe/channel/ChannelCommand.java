package com.eliaslucky.ibm_mainframe.channel;

/**
 * One channel command word. In the real S/360 a CCW is 8 bytes:
 * command byte, data address, flags, byte count. We keep the pieces
 * that matter for a mod — the op, the count, and the chain flag.
 *
 * @param op	 what the device should do
 * @param count  bytes to transfer, or 0 for ops that don't transfer
 * @param chain  if true, the channel fetches the next CCW after this
 *				 one completes; if false, the program ends
 * @param payload arg bytes for {@link Op#CONTROL}; may be {@code null}
 */
public record ChannelCommand(Op op, int count, boolean chain, byte[] payload) {
	public enum Op {
		/** Transfer from device into main storage. */
		READ,
		/** Transfer from main storage into device. */
		WRITE,
		/** Transfer sense bytes (status only). */
		SENSE,
		/** Device-specific control (rewind, load, eject). */
		CONTROL,
		/** No-op; used as a chain padding. */
		NOP
	}

	public ChannelCommand(Op op) { this(op, 0, false, null); }
	public ChannelCommand(Op op, int count) { this(op, count, false, null); }
}
