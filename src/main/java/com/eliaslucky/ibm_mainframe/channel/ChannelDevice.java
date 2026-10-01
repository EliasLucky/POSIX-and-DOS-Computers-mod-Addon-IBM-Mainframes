package com.eliaslucky.ibm_mainframe.channel;

import com.eliaslucky.mc_dos.api.hardware.Peripheral;

/**
 * A device that can sit on a channel cable.
 *
 * <p>Extends {@link Peripheral} so that all the existing mod
 * infrastructure (drivers, DeviceHandler, DeviceLookup) works unchanged.
 * The additional contract is that the device accepts a CCW and reports
 * back a status. Mainframe kernels call {@link #execute} directly; PC
 * kernels ignore it and use the byte-stream methods only.
 *
 * <h2>Connection rules</h2>
 * A device is on the network if it is orthogonally adjacent to any
 * cable block in that network. Typically that means sitting on top of
 * a cable. Implementations that need to trigger cache invalidation when
 * placed or broken should call
 * {@link ChannelNetworkManager#invalidateLevel(net.minecraft.world.level.Level)}
 * from {@code onPlace} and {@code onRemove}.
 */
public interface ChannelDevice extends Peripheral {
	/**
	 * Human-readable device name. Shown in {@code MSD}-style listings
	 * and referenced by operator commands.
	 *
	 * @return a short uppercase name, e.g. {@code "TAPE01"}, {@code "1403"}
	 */
	String deviceName();

	/**
	 * Execute one channel command against this device.
	 *
	 * <p>The channel program calls this; the device never initiates.
	 * Devices that do not implement a given op should return
	 * {@link ChannelStatus#COMMAND_REJECT}.
	 *
	 * @param cmd the command to execute; never {@code null}
	 * @return the device's status; never {@code null}
	 */
	ChannelStatus execute(ChannelCommand cmd);

	/** Device status codes. A subset of the real mainframe CSW bits. */
	enum ChannelStatus {
		/** Command completed, chain may continue. */
		OK,
		/** Command completed but the device needs attention (out of paper, end of tape). */
		UNIT_EXCEPTION,
		/** Device busy; retry. */
		BUSY,
		/** Command not implemented or invalid for this device. */
		COMMAND_REJECT,
		/** Device is not ready (no media, powered down, absent). */
		NOT_READY
	}
}
