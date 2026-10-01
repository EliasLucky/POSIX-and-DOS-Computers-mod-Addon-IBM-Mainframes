package com.eliaslucky.ibm_mainframe.channel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/**
 * One connected channel bus: the set of cable blocks reachable from a
 * starting cable by horizontal moves, plus every {@link ChannelDevice}
 * orthogonally adjacent to any of them.
 *
 * <p>Immutable. Rebuilt from scratch when the world changes.
 */
public final class ChannelNetwork {
    public static final ChannelNetwork EMPTY = new ChannelNetwork(Set.of(), List.of(), Map.of());

    private final Set<BlockPos> cables;
    private final List<ChannelDevice> devicesOrdered;
    private final Map<Integer, ChannelDevice> byAddress;
    private final Map<BlockPos, Integer> addressByPos; //TODO: SCRAP THIS

    private ChannelNetwork(Set<BlockPos> cables, List<ChannelDevice> devicesOrdered, Map<Integer, ChannelDevice> byAddress) {
        this.cables = cables;
        this.devicesOrdered = devicesOrdered;
        this.byAddress = byAddress;
        this.addressByPos = new HashMap<>();
        for (var e : byAddress.entrySet()) {
            // Reverse lookup: address -> pos would need the pos; reconstruct.
        }
        // Rebuild addressByPos from the ordered list, using the same walk.
        // (See scan() below; the constructor is private and only called
        //  by scan, which already knows the positions.)
    }

    public Set<BlockPos> cables() { return cables; }
    public List<ChannelDevice> devices() { return devicesOrdered; }
    public ChannelDevice byAddress(int address) { return byAddress.get(address); }
    public boolean isEmpty() { return devicesOrdered.isEmpty(); }

    /**
     * Flood-fill from {@code start}. Returns {@code null} if
     * {@code start} is not a cable block.
     */
    public static ChannelNetwork scan(Level level, BlockPos start) {
        if (!ChannelCableBlock.isCable(level.getBlockState(start))) return null;

        // Phase 1: gather every cable reachable by horizontal steps.
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

        // Phase 2: gather devices adjacent (any of 6 sides) to any cable.
        Map<BlockPos, ChannelDevice> byPos = new HashMap<>();
        for (BlockPos c : cables) {
            for (Direction d : Direction.values()) {
                BlockPos np = c.relative(d);
                if (cables.contains(np)) continue;
                if (byPos.containsKey(np)) continue;
                BlockEntity be = level.getBlockEntity(np);
                if (be instanceof ChannelDevice dev) {
                    byPos.put(np, dev);
                }
            }
        }

        // Phase 3: order devices deterministically (Y, then Z, then X)
        // so addresses are stable for a given physical layout.
        List<BlockPos> sorted = new ArrayList<>(byPos.keySet());
        sorted.sort(Comparator
                .comparingInt((BlockPos p) -> p.getY())
                .thenComparingInt(p -> p.getZ())
                .thenComparingInt(p -> p.getX()));

        List<ChannelDevice> ordered = new ArrayList<>(sorted.size());
        Map<Integer, ChannelDevice> byAddress = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            ChannelDevice dev = byPos.get(sorted.get(i));
            ordered.add(dev);
            byAddress.put(i, dev);
        }

        return new ChannelNetwork(cables, ordered, byAddress);
    }
}
