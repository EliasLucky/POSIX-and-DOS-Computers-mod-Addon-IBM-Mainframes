package com.eliaslucky.ibm_mainframe.jcl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * The job spool. Holds jobs waiting to run, the job currently
 * running, and completed jobs whose listings have not yet been
 * collected. Persisted to the kernel's NBT (via the containing
 * block entity, eventually) so the queue survives a world save.
 */
public final class SpoolQueue {
	private final Map<String, SpoolEntry> entries = new LinkedHashMap<>();
	private final Random rng = new Random();

	public SpoolEntry submit(String jobName, String jobClass, String msgClass, List<String> deck) {
		String id = String.format("%04X", rng.nextInt(0x10000));
		SpoolEntry e = new SpoolEntry(id, jobName, jobClass, msgClass, deck);
		entries.put(id, e);
		return e;
	}

	/** The oldest WAITING entry, or empty. */
	public Optional<SpoolEntry> nextWaiting() {
		for (SpoolEntry e : entries.values()) {
			if (e.status() == SpoolEntry.Status.WAITING) return Optional.of(e);
		}
		return Optional.empty();
	}

	public Optional<SpoolEntry> byId(String id) {
		return Optional.ofNullable(entries.get(id));
	}

	public List<SpoolEntry> all() { return List.copyOf(entries.values()); }

	public int size() { return entries.size(); }

	public void clear() { entries.clear(); }

	/** Remove all DONE and FAILED entries. Called after listings are collected. */
	public void purgeCompleted() {
		entries.values().removeIf(e ->
				e.status() == SpoolEntry.Status.DONE
						|| e.status() == SpoolEntry.Status.FAILED);
	}
}
