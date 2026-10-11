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
		return process(computer, rawInput, CommandOrigin.TERMINAL);
	}

	/**
	 * Process a command with an explicit origin. Called by the
	 * console packet handler; not part of the base mod's
	 * {@code ICommandProcessor} contract.
	 *
	 * <p>Currently the origin is only used to satisfy the
	 * {@code ROUTE} command, which is valid only at a console. All
	 * other commands ignore it.
	 */
	public String process(ComputerBlockEntity computer, String rawInput, CommandOrigin origin) {
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
		case "SUBMIT"  -> kernel.submitJob();
		case "JOBS"    -> kernel.listJobs();
		case "OUTPUT"  -> kernel.viewOutput(arg);
		case "DEVICES" -> listDevices(kernel);
		case "DSLIST"  -> dslist(kernel, arg);
		case "ROUTE"   -> kernel.setRoutesFromCommand(origin, arg);
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
                 SUBMIT    Read the next deck and enqueue it
                 START     Run the next queued job
                 JOBS      List the spool queue
                 OUTPUT    View a completed job's listing (OUTPUT <id>)
                 DEVICES   List channel-attached devices
                 DSLIST    List catalogued datasets (DSLIST MY.* to filter)
                 ROUTE     Set console message routing (ROUTE IEA IEF)
                 HELP      Show this message
			   """;
	}

	@Override public String getPrompt(String path)	   { return ""; }
	@Override public String defaultPath()			   { return "/"; }
	@Override public FileNamePolicy fileNamePolicy()   { return PosixFileNamePolicy.INSTANCE; }
	@Override public String osFamily()				   { return "os360"; }
	@Override public Kernel createKernel()			   { return new MainframeKernel(type); }
}
