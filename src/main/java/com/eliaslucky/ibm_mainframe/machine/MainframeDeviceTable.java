package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.ibm_mainframe.channel.ChannelDevice;
import com.eliaslucky.mc_dos.api.hardware.DeviceHandler;
import com.eliaslucky.mc_dos.api.hardware.DeviceLookup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The mainframe kernel's device table.
 *
 * <p>Every entry carries a unit address (the channel address the
 * device occupies), a device name (its {@link ChannelDevice#deviceName()}),
 * and the live {@link ChannelDevice}. The kernel looks devices up by
 * either key.
 *
 * <p>Unlike the DOS device table, this one keeps the CCW-capable
 * device rather than wrapping it in a {@link DeviceHandler}. Mainframe
 * programs issue CCWs directly; the byte-stream view is offered for
 * compatibility via {@link #lookup(String)}.
 */
public class MainframeDeviceTable implements DeviceLookup {
	/** One registered device. */
	public record Entry(int unit, String name, ChannelDevice device) {}

	private final Map<Integer, Entry> byUnit = new LinkedHashMap<>();
	private final Map<String, Entry> byName = new LinkedHashMap<>();

	/**
	 * Register a device at a unit address.
	 *
	 * @return {@code false} if the unit or name is already taken
	 */
	public boolean register(int unit, ChannelDevice dev) {
		if (byUnit.containsKey(unit)) return false;
		String name = dev.deviceName().toUpperCase(Locale.ROOT);
		if (name.isEmpty() || byName.containsKey(name)) return false;

		Entry e = new Entry(unit, name, dev);
		byUnit.put(unit, e);
		byName.put(name, e);
		return true;
	}

	public ChannelDevice byUnit(int unit) {
		Entry e = byUnit.get(unit);
		return e == null ? null : e.device();
	}

	public ChannelDevice byName(String name) {
		Entry e = byName.get(name.toUpperCase(Locale.ROOT));
		return e == null ? null : e.device();
	}

	public int unitOf(String name) {
		Entry e = byName.get(name.toUpperCase(Locale.ROOT));
		return e == null ? -1 : e.unit();
	}

	public List<Entry> all() { return List.copyOf(byUnit.values()); }

	public void clear() { byUnit.clear(); byName.clear(); }

	// --- DeviceLookup: byte-stream view for PC-style callers ------------

	@Override
	public boolean isDevice(String name) {
		return byName.containsKey(name.toUpperCase(Locale.ROOT));
	}

	@Override
	public DeviceHandler lookup(String name) {
		ChannelDevice d = byName(name);
		return d == null ? null : DeviceHandler.of(d);
	}

	@Override
	public List<String> names() {
		return List.copyOf(byName.keySet());
	}
}
