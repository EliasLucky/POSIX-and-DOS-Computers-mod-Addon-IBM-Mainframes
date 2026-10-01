package com.eliaslucky.ibm_mainframe.channel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-level cache of channel networks. Every cable block in a network
 * maps to the same {@link ChannelNetwork} instance, so lookups are
 * {@code O(1)} regardless of which cable the caller has a reference to.
 *
 * <p>The whole level's cache is dropped when any cable or channel
 * device is placed or broken. Networks are cheap to rebuild — the
 * flood fill is proportional to the number of cables, which is small
 * in every realistic base.
 */
public final class ChannelNetworkManager {
	private static final Map<Level, Map<BlockPos, ChannelNetwork>> CACHE = new HashMap<>();

	private ChannelNetworkManager() {}

	/**
	 * Return the network containing {@code anyCable}, building it if
	 * necessary. Returns {@link ChannelNetwork#EMPTY} if the block is
	 * not a cable.
	 */
	public static ChannelNetwork getOrBuild(Level level, BlockPos anyCable) {
		if (level.isClientSide()) {
			// Networks are only meaningful on the server. Return an
			// empty placeholder so client-side rendering code can
			// call this without crashing.
			return ChannelNetwork.EMPTY;
		}
		Map<BlockPos, ChannelNetwork> levelCache =
				CACHE.computeIfAbsent(level, k -> new HashMap<>());
		ChannelNetwork cached = levelCache.get(anyCable);
		if (cached != null) return cached;

		ChannelNetwork fresh = ChannelNetwork.scan(level, anyCable);
		if (fresh == null) return ChannelNetwork.EMPTY;

		for (BlockPos c : fresh.cables()) levelCache.put(c, fresh);
		return fresh;
	}

	/**
	 * Drop the cache for a level. Call from any block's
	 * {@code onPlace} / {@code onRemove} if the block participates in
	 * a channel network (cables and channel devices both qualify).
	 */
	public static void invalidateLevel(Level level) {
		CACHE.remove(level);
	}
}
