package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.mc_dos.api.hardware.Kernel;
import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;
import com.eliaslucky.mc_dos.blocks.computer.fs.FileNamePolicy;
import com.eliaslucky.mc_dos.blocks.computer.fs.PosixFileNamePolicy;
import com.eliaslucky.mc_dos.blocks.computer.processors.ICommandProcessor;

import java.util.Locale;

/**
 * Operator console for a mainframe.
 *
 * <p>One instance per {@link MainframeType}. Holds a back-reference
 * to its type so that kernels it creates can inspect the machine's
 * hardware identity.
 */
public class MainframeCommandProcessor implements ICommandProcessor {
	private final MainframeType type;

	public MainframeCommandProcessor(MainframeType type) {
		this.type = type;
	}

	@Override
	public String process(ComputerBlockEntity computer, String rawInput) {
		String input = rawInput == null ? "" : rawInput.trim();
		if (input.isEmpty()) return "";

		Kernel k = computer.getKernel();
		if (!(k instanceof MainframeKernel kernel)) {
			return "IEE100I KERNEL NOT READY";
		}

		String upper = input.toUpperCase(Locale.ROOT);
		String[] parts = upper.split("\\s+",2);
		String cmd = parts[0];
		String arg = parts.length > 1 ? parts[1] : "";

		return switch (cmd) {
			case "START"   -> kernel.runNextJob();
			case "DEVICES" -> listDevices(kernel);
			case "OUTPUT"  -> dumpOrphanOutput(kernel);
			case "DSLIST"  -> dslist(kernel,arg);
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

	private static String dslist(MainframeKernel kernel, String filter) {
		if (kernel.catalog() == null) return "IEE608I CATALOG NOT AVAILABLE";
		var names = kernel.catalog().listAll();
		if (names.isEmpty()) return "IEE609I CATALOG IS EMPTY";

		String prefix = filter == null ? "" : filter.replace("*", "").trim();
		StringBuilder sb = new StringBuilder("IEE608I CATALOG CONTENTS:");
		int shown = 0;
		for (String dsn : names) {
			if (!prefix.isEmpty() && !dsn.startsWith(prefix)) continue;

			// Try to load the descriptor for a size report.
			String volume = "UNKNOWN";
			int size = -1;
			var opt = kernel.catalog().lookup(dsn);
			if (opt.isPresent()) {
				var ds = opt.get();
				volume = ds.descriptor().volumeSerial();
				size = ds.size();
			}
			sb.append(String.format("%n  %-24s %-8s %s", dsn, volume, size < 0 ? "(not mounted)" : size + " records"));
			shown++;
		}
		if (shown == 0) return "IEE608I NO DATASETS MATCH '" + filter + "'";
		sb.insert(0, "IEE608I " + shown + " DATASET(S) LISTED\n");
		return sb.toString();
	}

	private static String helpText() {
		return """
			   IEE500I MAINFRAME OPERATOR COMMANDS
				 START	   Read and run the next job from the card reader
				 DEVICES   List channel-attached devices with unit addresses
				 OUTPUT    View listings held because no printer is attached
				 DSLIST    List catalogued datasets
						   DSLIST MY.* to filter by prefix
				 HELP	   Show this message
			   """;
	}

	@Override
	public Kernel createKernel() {
		return new MainframeKernel(type);
	}
	@Override public String getPrompt(String path)	   { return ""; }
	@Override public String defaultPath()			   { return "/"; }
	@Override public FileNamePolicy fileNamePolicy()   { return PosixFileNamePolicy.INSTANCE; }
	@Override public String osFamily()				   { return "os360"; }
	@Override public Kernel createKernel()			   { return new MainframeKernel(); }
}
