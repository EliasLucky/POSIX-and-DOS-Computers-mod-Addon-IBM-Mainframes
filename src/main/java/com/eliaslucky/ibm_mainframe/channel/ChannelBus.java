package com.eliaslucky.ibm_mainframe.channel;

import com.eliaslucky.mc_dos.api.hardware.Peripheral;
import com.eliaslucky.mc_dos.api.hardware.PeripheralAddress;
import com.eliaslucky.mc_dos.api.hardware.PeripheralBus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link PeripheralBus} that spans a channel cable network rather
 * than the six faces of a computer block.
 *
 * <p>The constructor looks for any cable orthogonally adjacent to the
 * computer and asks {@link ChannelNetworkManager} for the network that
 * contains it. If no cable touches the machine, the bus is empty —
 * a mainframe without cables has no devices.
 */
public class ChannelBus implements PeripheralBus {
	private final ChannelNetwork network;
	private final Map<PeripheralAddress, ChannelDevice> byAddress = new HashMap<>();

	public ChannelBus(Level level, BlockPos computerPos) {
		BlockPos seed = null;
		for (Direction d : Direction.values()) {
			BlockPos np = computerPos.relative(d);
			if (ChannelCableBlock.isCable(level.getBlockState(np))) {
				seed = np;
				break;
			}
		}
		this.network = (seed == null)
				? ChannelNetwork.EMPTY
				: ChannelNetworkManager.getOrBuild(level, seed);
	}

	/** Direct access for mainframe kernels that need CCW dispatch. */
	public ChannelNetwork network() { return network; }

	@Override
	public List<PeripheralAddress> scan() {
		List<PeripheralAddress> out = new ArrayList<>(network.devices().size());
		Map<String, Integer> slotCounters = new HashMap<>();

		for (int i = 0; i < network.devices().size(); i++) {
			ChannelDevice dev = network.devices().get(i);
			if (!dev.isReady()) continue;

			int slot = slotCounters.merge(dev.deviceClass(), 1, Integer::sum) - 1;
			PeripheralAddress addr = new PeripheralAddress(
					dev.deviceClass(),
					dev.vendorId(),
					dev.productId(),
					slot,
					BlockPos.ZERO);  // TODO: base mod API requires worldPos for persistence. But channel device's identity is its position in the network and not its coordinates. Re-write PeripheralAddress later so it carries network slot and a nullable position.
			out.add(addr);
			byAddress.put(addr, dev);
		}
		return out;
	}

	@Override
	public Peripheral get(PeripheralAddress addr) {
		return byAddress.get(addr);
	}
}
