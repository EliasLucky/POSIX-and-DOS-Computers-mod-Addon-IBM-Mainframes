package com.eliaslucky.ibm_mainframe.machine;

import java.util.Set;

/**
 * Message classes for the Multiple Console Support (MCS) routing
 * scheme. The class of a message is its first three characters,
 * upper-cased. A console receives a message if its route set
 * contains the message's class.
 *
 * <p>Unknown prefixes fall through to {@link #UNKNOWN}. A console
 * whose set contains {@link #ALL} receives every message, matching
 * the master console on a real installation.
 */
public enum RouteCode {
	/** Hardware and configuration. IPL, device scan, unit status. */
	IEA,
	/** Operator and system. Operator commands, job control. */
	IEE,
	/** Job and scheduler. Job started, ended, abended. */
	IEF,
	/** FORTRAN compiler diagnostics. */
	IHC,
	/** Fallback for anything unrecognized. */
	UNKNOWN,
	/** Catch-all. A console with ALL receives everything. */
	ALL;

	/** Default set for a master console: everything. */
	public static final Set<RouteCode> MASTER = Set.of(ALL);

	/** Route set for a subsystem console that only wants job output. */
	public static final Set<RouteCode> JOB_WATCHER = Set.of(IEF, IEE);

	/** Route set for a hardware-watch console. */
	public static final Set<RouteCode> HARDWARE_WATCHER = Set.of(IEA);

	/**
	 * Classify a message by its leading characters. Messages that
	 * do not start with a recognized three-character prefix are
	 * classified as {@link #UNKNOWN}.
	 */
	public static RouteCode of(String line) {
		if (line == null || line.length() < 3) return UNKNOWN;
		String prefix = line.substring(0, 3).toUpperCase(java.util.Locale.ROOT);
		return switch (prefix) {
			case "IEA" -> IEA;
			case "IEE" -> IEE;
			case "IEF" -> IEF;
			case "IHC" -> IHC;
			default    -> UNKNOWN;
		};
	}
}
