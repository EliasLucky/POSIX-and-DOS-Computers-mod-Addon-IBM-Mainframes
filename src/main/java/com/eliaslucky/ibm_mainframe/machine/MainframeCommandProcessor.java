package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.mc_dos.api.hardware.Kernel;
import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;
import com.eliaslucky.mc_dos.blocks.computer.fs.FileNamePolicy;
import com.eliaslucky.mc_dos.blocks.computer.fs.PosixFileNamePolicy;
import com.eliaslucky.mc_dos.blocks.computer.processors.ICommandProcessor;

import java.util.Locale;

/**
 * Operator console for a mainframe. The commands are the ones an
 * operator typed at the 1052 console.
 *
 * <p>vsupports:
 * <ul>
 *	 <li>{@code START} read and run the next job from the card reader</li>
 *	 <li>{@code DEVICES} list registered channel devices</li>
 *	 <li>{@code HELP} brief command summary</li>
 * </ul>
 */
public class MainframeCommandProcessor implements ICommandProcessor {
	@Override
	public String process(ComputerBlockEntity computer, String rawInput) {
		String input = rawInput == null ? "" : rawInput.trim();
		if (input.isEmpty()) return "";

		Kernel k = computer.getKernel();
		if (!(k instanceof MainframeKernel kernel)) {
			return "IEE100I KERNEL NOT READY";
		}

		String upper = input.toUpperCase(Locale.ROOT);
		String[] parts = upper.split("\\s+", 2);
		String cmd = parts[0];

		return switch (cmd) {
			case "START"   -> kernel.runNextJob();
			case "DEVICES" -> listDevices(kernel);
			case "HELP"    -> helpText();
			default		   -> "IEE300I COMMAND NOT RECOGNIZED: " + cmd;
		};
	}

	private static String listDevices(MainframeKernel kernel) {
		var names = kernel.deviceTable().names();
		if (names.isEmpty()) return "IEE301I NO DEVICES REGISTERED";
		StringBuilder sb = new StringBuilder("IEE302I CONFIGURED DEVICES:");
		for (String n : names) {
			sb.append("\n  ").append(n).append("  ")
			  .append(kernel.deviceTable().device(n).description());
		}
		return sb.toString();
	}

	private static String helpText() {
		return """
			   IEE500I MAINFRAME OPERATOR COMMANDS
				 START	   Read and run the next job from the card reader
				 DEVICES   List channel-attached devices
				 HELP	   Show this message
			   """;
	}

	@Override public String getPrompt(String path) { return ""; }
	@Override public String defaultPath()		   { return "/"; }
	@Override public FileNamePolicy fileNamePolicy() { return PosixFileNamePolicy.INSTANCE; }
	@Override public String osFamily()			   { return "os360"; }
	@Override public Kernel createKernel()		   { return new MainframeKernel(); }
}
