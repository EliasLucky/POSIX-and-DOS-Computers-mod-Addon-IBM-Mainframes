package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.mc_dos.api.hardware.Kernel;
import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;
import com.eliaslucky.mc_dos.blocks.computer.fs.FileNamePolicy;
import com.eliaslucky.mc_dos.blocks.computer.fs.PosixFileNamePolicy;
import com.eliaslucky.mc_dos.blocks.computer.processors.ICommandProcessor;

import java.util.Locale;

/**
 * Operator console for a mainframe. The commands mirror what a real
 * operator typed at the 1052: short, terse, and about the machine
 * rather than the workload.
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
		String cmd = upper.split("\\s+", 2)[0];

		return switch (cmd) {
			case "START"   -> kernel.runNextJob();
			case "DEVICES" -> listDevices(kernel);
			case "OUTPUT"  -> dumpOrphanOutput(kernel);
			case "HELP"    -> helpText();
			default		   -> "IEE300I COMMAND NOT RECOGNIZED: " + cmd;
		};
	}

	private static String listDevices(MainframeKernel kernel) {
		var entries = kernel.deviceTable().all();
		if (entries.isEmpty()) return "IEE301I NO DEVICES REGISTERED";

		StringBuilder sb = new StringBuilder("IEE302I CONFIGURED DEVICES:");
		for (var e : entries) {
			sb.append("\n  UNIT ").append(String.format("%03d", e.unit()))
			  .append("  ").append(String.format("%-8s", e.name()))
			  .append("  ").append(e.device().description());
		}
		return sb.toString();
	}

	private static String dumpOrphanOutput(MainframeKernel kernel) {
		if (!kernel.hasOrphanOutput()) return "IEE606I NO HELD OUTPUT";

		var lines = kernel.drainOrphanOutput();
		StringBuilder sb = new StringBuilder("IEE607I HELD LISTING -- ")
				.append(lines.size()).append(" LINES");
		for (String l : lines) sb.append("\n").append(l);
		return sb.toString();
	}

	private static String helpText() {
		return """
			   IEE500I MAINFRAME OPERATOR COMMANDS
				 START	   Read and run the next job from the card reader
				 DEVICES   List channel-attached devices with unit addresses
				 OUTPUT    View listings held because no printer is attached
				 HELP	   Show this message
			   """;
	}

	@Override public String getPrompt(String path)	   { return ""; }
	@Override public String defaultPath()			   { return "/"; }
	@Override public FileNamePolicy fileNamePolicy()   { return PosixFileNamePolicy.INSTANCE; }
	@Override public String osFamily()				   { return "os360"; }
	@Override public Kernel createKernel()			   { return new MainframeKernel(); }
}
