package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.mc_dos.api.hardware.DeviceHandler;
import com.eliaslucky.mc_dos.api.hardware.DeviceLookup;
import com.eliaslucky.mc_dos.api.hardware.Kernel;
import com.eliaslucky.mc_dos.api.hardware.PeripheralBus;
import com.eliaslucky.mc_dos.blocks.computer.VirtualFileSystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v1 mainframe kernel. Boots, reports nothing, has no devices. The
 * channel bus is available at boot and will be stored here once the
 * card-reader driver exists.
 */
public class MainframeKernel implements Kernel {
	private final List<String> bootLog = new ArrayList<>();
	private final SimpleTable devices = new SimpleTable();

	@Override public void boot(PeripheralBus bus, VirtualFileSystem vfs) {
		bootLog.clear();
		devices.clear();
		// TODO: load card-reader driver against `bus`, register device.
	}

	@Override public void shutdown() {
		bootLog.clear();
		devices.clear();
	}

	@Override public List<String> getBootLog() { return bootLog; }
	@Override public DeviceLookup getDevices() { return devices; }

	private static final class SimpleTable implements DeviceLookup {
		private final Map<String, DeviceHandler> byName = new LinkedHashMap<>();
		void clear() { byName.clear(); }
		@Override public boolean isDevice(String n) { return byName.containsKey(n.toUpperCase(Locale.ROOT)); }
		@Override public DeviceHandler lookup(String n) { return byName.get(n.toUpperCase(Locale.ROOT)); }
		@Override public List<String> names() { return List.copyOf(byName.keySet()); }
	}
}
