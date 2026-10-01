package com.eliaslucky.ibm_mainframe.channel;

import org.jetbrains.annotations.Nullable;

/**
 * What a device returns from one CCW. {@code data} is non-null only
 * for READ commands that transferred bytes.
 *
 * @param status the CSW status bits
 * @param data	 the transferred bytes for a READ, or {@code null}
 */
public record ChannelResult(ChannelDevice.ChannelStatus status, @Nullable byte[] data) {
	public static final ChannelResult OK		   = new ChannelResult(ChannelDevice.ChannelStatus.OK, null);
	public static final ChannelResult BUSY		   = new ChannelResult(ChannelDevice.ChannelStatus.BUSY, null);
	public static final ChannelResult NOT_READY    = new ChannelResult(ChannelDevice.ChannelStatus.NOT_READY, null);
	public static final ChannelResult REJECT	   = new ChannelResult(ChannelDevice.ChannelStatus.COMMAND_REJECT, null);
	public static final ChannelResult UNIT_EXCEPTION = new ChannelResult(ChannelDevice.ChannelStatus.UNIT_EXCEPTION, null);

	public static ChannelResult read(byte[] data) {
		return new ChannelResult(ChannelDevice.ChannelStatus.OK, data);
	}

	public boolean success()  { return status == ChannelDevice.ChannelStatus.OK; }
	public boolean hasData()  { return data != null && data.length > 0; }
}
