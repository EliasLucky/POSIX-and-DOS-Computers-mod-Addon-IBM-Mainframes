package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.ibm_mainframe.blocks.ConsoleBlockEntity;

/**
 * The origin of a command issued to a mainframe. Only the addon
 * cares about this: the base mod's {@code ICommandProcessor}
 * interface has no notion of an issuing console, and the DOS and
 * UNIX shells don't either.
 *
 * <p>An origin is passed through {@link MainframeCommandProcessor}
 * when a command arrives from a console. Commands from a terminal
 * or from internal code use {@link #TERMINAL}.
 *
 * @param kind	 a short tag: {@code "terminal"}, {@code "console"},
 *				 {@code "internal"}
 * @param label  a human-readable identifier, e.g. the console model
 *				 ({@code "1052"}, {@code "3270"}), or the empty string
 * @param source the live {@link ConsoleBlockEntity} that issued the
 *				 command, or {@code null}
 */
public record CommandOrigin(String kind, String label, ConsoleBlockEntity source) {
	public static final CommandOrigin TERMINAL = new CommandOrigin("terminal", "", null);

	public static final CommandOrigin INTERNAL = new CommandOrigin("internal", "", null);

	public static CommandOrigin console(ConsoleBlockEntity c) {
		if (c == null) return TERMINAL;
		return new CommandOrigin("console", c.consoleModel(), c);
	}

	public boolean isConsole() { return "console".equals(kind); }
}
