package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.ibm_mainframe.channel.ChannelDevice;
import com.eliaslucky.mc_dos.api.hardware.DeviceHandler;
import com.eliaslucky.mc_dos.api.hardware.DeviceLookup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Devices registered on a mainframe kernel, keyed by their
 * {@link ChannelDevice#deviceName()}. Unlike the DOS device table,
 * this one keeps the {@link ChannelDevice} itself so the kernel can
 * issue CCWs directly, not just push bytes through a handler.
 */
public class MainframeDeviceTable implements DeviceLookup {
	private final Map<String, ChannelDevice> devices = new LinkedHashMap<>();

	public boolean register(ChannelDevice dev) {
		String key = dev.deviceName().toUpperCase(Locale.ROOT);
		if (key.isEmpty() || devices.containsKey(key)) return false;
		devices.put(key, dev);
		return true;
	}

	/** The CCW-capable device, or {@code null}. */
	public ChannelDevice device(String name) {
		return devices.get(name.toUpperCase(Locale.ROOT));
	}

	public void clear() { devices.clear(); }

	// --- DeviceLookup: byte-stream view for legacy PC-style callers ---

	@Override public boolean isDevice(String name) {
		return devices.containsKey(name.toUpperCase(Locale.ROOT));
	}

	@Override public DeviceHandler lookup(String name) {
		ChannelDevice d = device(name);
		return d == null ? null : DeviceHandler.of(d);
	}

	@Override public List<String> names() {
		return List.copyOf(devices.keySet());
	}
}
