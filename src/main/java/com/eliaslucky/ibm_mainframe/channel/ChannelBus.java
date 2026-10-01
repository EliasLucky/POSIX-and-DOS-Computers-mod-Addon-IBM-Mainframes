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
 * A {@link PeripheralBus} backed by a channel cable network.
 *
 * <p>The constructor looks for a cable orthogonally adjacent to the
 * computer. If found, the whole network is resolved through
 * {@link ChannelNetworkManager}. The bus then reports:
 * <ul>
 *	 <li>the network itself (for kernels that need CCW dispatch)</li>
 *	 <li>whether the network is shared with another mainframe
 *		 ({@link #multipleCpus()})</li>
 * </ul>
 */
public class ChannelBus implements PeripheralBus {
	private final ChannelNetwork network;
	private final boolean multipleCpus;
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

		if (seed == null) {
			this.network = ChannelNetwork.EMPTY;
			this.multipleCpus = false;
			return;
		}

		ChannelNetwork net = ChannelNetworkManager.getOrBuild(level, seed);
		// The calling CPU is itself adjacent to a cable, so it appears
		// in net.cpus(). More than one entry means a second cabinet is
		// also on this channel.
		this.multipleCpus = net.cpus().size() > 1;
		this.network = net;
	}

	/** The raw network, for kernels that need CCW dispatch. */
	public ChannelNetwork network() { return network; }

	/** Whether another mainframe cabinet shares this channel. */
	public boolean multipleCpus() { return multipleCpus; }

	@Override
	public List<PeripheralAddress> scan() {
		List<PeripheralAddress> out = new ArrayList<>(network.devices().size());
		Map<String, Integer> slotCounters = new HashMap<>();

		int address = 0;
		for (ChannelDevice dev : network.devices()) {
			if (!dev.isReady()) { address++; continue; }

			int slot = slotCounters.merge(dev.deviceClass(), 1, Integer::sum) - 1;
			PeripheralAddress.Channel addr = new PeripheralAddress.Channel(
					dev.deviceClass(),
					dev.vendorId(),
					dev.productId(),
					slot,
					"unit:" + address,
					null);
			out.add(addr);
			byAddress.put(addr, dev);
			address++;
		}
		return out;
	}

	@Override
	public Peripheral get(PeripheralAddress addr) {
		return byAddress.get(addr);
	}
}
