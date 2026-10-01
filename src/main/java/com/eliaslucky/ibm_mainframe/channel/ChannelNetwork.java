package com.eliaslucky.ibm_mainframe.channel;

import com.eliaslucky.ibm_mainframe.machine.MainframeType;
import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One connected channel bus: the set of cable blocks reachable from a
 * starting cable by horizontal flood-fill, the devices adjacent to
 * those cables, and the CPU cabinets also adjacent to those cables.
 *
 * <p>Every device gets a unit address (0-based, in a deterministic
 * Y-Z-X sort of block positions). Addresses are stable for a given
 * physical layout and are reassigned whenever the network is rebuilt.
 *
 * <p>The {@link #cpus()} set contains every mainframe cabinet that
 * touches this network. A well-formed network has exactly one. Two
 * CPUs sharing a channel is a misconfiguration the kernel refuses to
 * boot through — see {@link ChannelBus#multipleCpus()}.
 *
 * <p>Immutable. Rebuilt from scratch when the world changes.
 */
public final class ChannelNetwork {
	/** An empty network. Used when a bus has no cables attached. */
	public static final ChannelNetwork EMPTY = new ChannelNetwork(
			Set.of(), List.of(), Map.of(), Set.of());

	private final Set<BlockPos> cables;
	private final List<ChannelDevice> devicesOrdered;
	private final Map<Integer, ChannelDevice> byAddress;
	private final Set<BlockPos> cpuPositions;

	private ChannelNetwork(Set<BlockPos> cables,
						   List<ChannelDevice> devicesOrdered,
						   Map<Integer, ChannelDevice> byAddress,
						   Set<BlockPos> cpuPositions) {
		this.cables = cables;
		this.devicesOrdered = devicesOrdered;
		this.byAddress = byAddress;
		this.cpuPositions = cpuPositions;
	}

	public Set<BlockPos> cables()			  { return cables; }
	public List<ChannelDevice> devices()	  { return devicesOrdered; }
	public ChannelDevice byAddress(int addr)  { return byAddress.get(addr); }
	public Set<BlockPos> cpus()				  { return cpuPositions; }
	public boolean isEmpty()				  { return cables.isEmpty(); }

	/** The unit address of a specific device, or {@code -1} if not found. */
	public int addressOf(ChannelDevice dev) {
		for (Map.Entry<Integer, ChannelDevice> e : byAddress.entrySet()) {
			if (e.getValue() == dev) return e.getKey();
		}
		return -1;
	}

	/**
	 * Flood-fill from {@code start}. Returns {@code null} if
	 * {@code start} is not a cable block.
	 */
	public static ChannelNetwork scan(Level level, BlockPos start) {
		if (!ChannelCableBlock.isCable(level.getBlockState(start))) return null;

		// Phase 1: horizontal flood-fill over cables.
		Set<BlockPos> cables = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		cables.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos np = p.relative(d);
				if (cables.contains(np)) continue;
				if (ChannelCableBlock.isCable(level.getBlockState(np))) {
					cables.add(np);
					queue.add(np);
				}
			}
		}

		// Phase 2: any block entity orthogonally adjacent to any cable
		// is either a ChannelDevice (a peripheral) or a mainframe CPU.
		Map<BlockPos, ChannelDevice> devicesByPos = new HashMap<>();
		Set<BlockPos> cpus = new HashSet<>();

		for (BlockPos c : cables) {
			for (Direction d : Direction.values()) {
				BlockPos np = c.relative(d);
				if (cables.contains(np)) continue;
				if (devicesByPos.containsKey(np) || cpus.contains(np)) continue;
				BlockEntity be = level.getBlockEntity(np);
				if (be instanceof ChannelDevice dev) {
					devicesByPos.put(np, dev);
				}
				else if (be instanceof ComputerBlockEntity cpu
						&& cpu.getMachineType() instanceof MainframeType) {
					cpus.add(np);
				}
			}
		}

		// Phase 3: deterministic address order (Y, then Z, then X).
		List<BlockPos> sorted = new ArrayList<>(devicesByPos.keySet());
		sorted.sort(Comparator
				.comparingInt((BlockPos p) -> p.getY())
				.thenComparingInt(p -> p.getZ())
				.thenComparingInt(p -> p.getX()));

		List<ChannelDevice> ordered = new ArrayList<>(sorted.size());
		Map<Integer, ChannelDevice> byAddress = new LinkedHashMap<>();
		for (int i = 0; i < sorted.size(); i++) {
			ChannelDevice dev = devicesByPos.get(sorted.get(i));
			ordered.add(dev);
			byAddress.put(i, dev);
		}

		return new ChannelNetwork(
				Collections.unmodifiableSet(cables),
				Collections.unmodifiableList(ordered),
				Collections.unmodifiableMap(byAddress),
				Collections.unmodifiableSet(cpus));
	}
}
