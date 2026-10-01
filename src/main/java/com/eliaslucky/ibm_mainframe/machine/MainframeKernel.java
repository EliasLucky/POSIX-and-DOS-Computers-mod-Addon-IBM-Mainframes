package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.ibm_mainframe.channel.ChannelBus;
import com.eliaslucky.ibm_mainframe.channel.ChannelCommand;
import com.eliaslucky.ibm_mainframe.channel.ChannelDevice;
import com.eliaslucky.ibm_mainframe.channel.ChannelNetwork;
import com.eliaslucky.ibm_mainframe.channel.ChannelResult;
import com.eliaslucky.ibm_mainframe.jcl.JclParser;
import com.eliaslucky.ibm_mainframe.jcl.Job;
import com.eliaslucky.ibm_mainframe.jcl.JobContext;
import com.eliaslucky.ibm_mainframe.jcl.ProgramRegistry;
import com.eliaslucky.ibm_mainframe.programs.FortProgram;
import com.eliaslucky.mc_dos.api.hardware.DeviceLookup;
import com.eliaslucky.mc_dos.api.hardware.Kernel;
import com.eliaslucky.mc_dos.api.hardware.PeripheralBus;
import com.eliaslucky.mc_dos.blocks.computer.VirtualFileSystem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The mainframe kernel. Owns a device table, a role assignment, a boot
 * log, and a batch job runner.
 *
 * <h2>Boot</h2>
 * <ol>
 *	 <li>Refuse to IPL if the channel is shared with another CPU.</li>
 *	 <li>Scan the bus and register every {@link ChannelDevice} at its
 *		 unit address.</li>
 *	 <li>Assign roles: first printer → SYSPRINT, first card reader →
 *		 SYSIN.</li>
 *	 <li>Log missing roles as warnings (the machine still boots).</li>
 * </ol>
 *
 * <h2>Running</h2>
 * {@link #runNextJob()} pulls cards from the SYSIN reader, parses
 * them as JCL, executes the steps, and writes listings through
 * SYSPRINT. If no printer exists, output accumulates in an orphan
 * buffer the operator can drain with the {@code OUTPUT} command.
 */
public class MainframeKernel implements Kernel {
	/** A logical slot the kernel needs filled by some device. */
	public enum Role {
		/** Where SYSPRINT output goes. */
		SYSPRINT_PRINTER,
		/** Where SYSIN data is read from. */
		SYSIN_READER
	}

	private final MainframeDeviceTable devices = new MainframeDeviceTable();
	private final Map<Role, Integer> roleToUnit = new EnumMap<>(Role.class);
	private final List<String> bootLog = new ArrayList<>();
	private final List<String> orphanOutput = new ArrayList<>();

	private boolean bootOk = false;
	private boolean warnedNoPrinter = false;

	public MainframeKernel() {
		ProgramRegistry.register("FORT", new FortProgram());
	}

	// --- Kernel lifecycle -------------------------------------------------

	@Override
	public void boot(PeripheralBus bus, VirtualFileSystem vfs) {
		devices.clear();
		roleToUnit.clear();
		bootLog.clear();
		orphanOutput.clear();
		bootOk = false;
		warnedNoPrinter = false;

		if (!(bus instanceof ChannelBus cb)) {
			bootLog.add("IEA901E NO CHANNEL BUS AVAILABLE");
			return;
		}
		if (cb.multipleCpus()) {
			bootLog.add("IEA902E MULTIPLE PROCESSORS DETECTED ON CHANNEL");
			bootLog.add("IEA903E IPL FAILED -- SEPARATE THE CHANNEL NETWORKS");
			return;
		}

		ChannelNetwork net = cb.network();
		if (net.isEmpty()) {
			bootLog.add("IEA901E NO CHANNEL CABLES ATTACHED");
			return;
		}

		int address = 0;
		for (ChannelDevice dev : net.devices()) {
			int unit = address;
			address++;

			if (!dev.isReady()) {
				bootLog.add("IEA904I UNIT " + unit + " " + dev.deviceName()
						+ " NOT READY");
				continue;
			}
			if (devices.register(unit, dev)) {
				bootLog.add("IEA900I UNIT " + unit + " "
						+ String.format("%-8s", dev.deviceName())
						+ " " + dev.description());
			} else {
				bootLog.add("IEA905W UNIT " + unit + " NOT REGISTERED");
			}
		}

		assignRoles();

		if (roleToUnit.get(Role.SYSIN_READER) == null) {
			bootLog.add("IEE600W NO CARD READER -- START UNAVAILABLE");
		}
		if (roleToUnit.get(Role.SYSPRINT_PRINTER) == null) {
			bootLog.add("IEE605W NO PRINTER -- LISTINGS HELD IN MEMORY");
		}

		bootOk = true;
	}

	private void assignRoles() {
		for (MainframeDeviceTable.Entry e : devices.all()) {
			String cls = e.device().deviceClass();
			if (cls.equals("printer")
					&& !roleToUnit.containsKey(Role.SYSPRINT_PRINTER)) {
				roleToUnit.put(Role.SYSPRINT_PRINTER, e.unit());
			}
			if (cls.equals("card_reader")
					&& !roleToUnit.containsKey(Role.SYSIN_READER)) {
				roleToUnit.put(Role.SYSIN_READER, e.unit());
			}
		}
	}

	@Override
	public void shutdown() {
		devices.clear();
		roleToUnit.clear();
		bootLog.clear();
		orphanOutput.clear();
		bootOk = false;
	}

	@Override public List<String> getBootLog() { return List.copyOf(bootLog); }
	@Override public DeviceLookup getDevices() { return devices; }

	public MainframeDeviceTable deviceTable() { return devices; }
	public boolean isBootOk() { return bootOk; }

	/** The assigned SYSPRINT printer, or {@code null} if none exists. */
	public ChannelDevice printer() {
		Integer u = roleToUnit.get(Role.SYSPRINT_PRINTER);
		return u == null ? null : devices.byUnit(u);
	}

	/** The assigned SYSIN reader, or {@code null} if none exists. */
	public ChannelDevice reader() {
		Integer u = roleToUnit.get(Role.SYSIN_READER);
		return u == null ? null : devices.byUnit(u);
	}

	// --- Listing output ---------------------------------------------------

	/**
	 * Write one record to the SYSPRINT printer. Falls back to an
	 * in-memory buffer if no printer is attached, warning the operator
	 * exactly once per boot.
	 */
	public void writeListing(String record) {
		ChannelDevice p = printer();
		if (p == null) {
			orphanOutput.add(record);
			if (!warnedNoPrinter) {
				warnedNoPrinter = true;
				bootLog.add("IEE605W NO PRINTER -- USE 'OUTPUT' TO VIEW LISTINGS");
			}
			return;
		}
		byte[] bytes = (record + "\n").getBytes(StandardCharsets.US_ASCII);
		p.execute(new ChannelCommand(
				ChannelCommand.Op.WRITE, bytes.length, false, bytes));
	}

	/** Take and clear the held listing buffer. */
	public List<String> drainOrphanOutput() {
		List<String> out = List.copyOf(orphanOutput);
		orphanOutput.clear();
		return out;
	}

	public boolean hasOrphanOutput() { return !orphanOutput.isEmpty(); }

	// --- Job execution ----------------------------------------------------

	/**
	 * Read the next job from the SYSIN reader and run it. Returns an
	 * operator-facing summary.
	 */
	public String runNextJob() {
		if (!bootOk) return "IEE100I SYSTEM NOT IPL'D";

		ChannelDevice r = reader();
		if (r == null) return "IEE600I NO CARD READER CONFIGURED";

		// Phase 1: read cards until end of deck or the first bare "//".
		List<String> cards = new ArrayList<>();
		boolean sawJobCard = false;

		while (true) {
			ChannelResult res = r.execute(
					new ChannelCommand(ChannelCommand.Op.READ, 80));

			if (res.status() == ChannelDevice.ChannelStatus.UNIT_EXCEPTION) break;
			if (!res.success() || !res.hasData()) break;

			String card = new String(res.data(), StandardCharsets.US_ASCII);
			boolean jcl = card.length() >= 2
					&& card.charAt(0) == '/' && card.charAt(1) == '/';

			if (jcl && card.trim().equals("//")) {
				cards.add(card);
				break;
			}
			if (jcl && card.toUpperCase(Locale.ROOT).contains(" JOB ")) {
				sawJobCard = true;
			}
			cards.add(card);
		}

		if (cards.isEmpty()) return "IEE601I NO JOBS IN INPUT QUEUE";
		if (!sawJobCard) return "IEE602I INPUT DOES NOT BEGIN WITH A JOB CARD";

		// Phase 2: parse and run.
		JclParser.Result parsed = JclParser.parse(cards);
		for (String w : parsed.warnings()) {
			writeListing("IEE700W " + w);
		}
		if (parsed.jobs().isEmpty()) return "IEE603I JOB CARD REJECTED";

		Job job = parsed.jobs().get(0);
		writeListing("");
		writeListing("IEF142I JOB " + job.name() + " STARTED");

		JobContext ctx = new KernelJobContext();
		for (Job.Step step : job.steps()) {
			executeStep(step, ctx);
		}

		writeListing("IEF142I JOB " + job.name() + " ENDED");
		return "IEE604I JOB " + job.name() + " COMPLETE";
	}

	private void executeStep(Job.Step step, JobContext ctx) {
		ProgramRegistry.Program program = ProgramRegistry.get(step.program());
		if (program == null) {
			writeListing("IEF212I STEP " + step.name() + " -- PROGRAM "
					+ step.program() + " NOT FOUND");
			return;
		}
		writeListing("IEF142I STEP " + step.name() + " -- EXEC PGM=" + step.program());

		CursorContext cursor = new CursorContext(step);
		program.run(step, cursor);
	}

	// --- JobContext implementations ---------------------------------------

	/** A bare job context used when no step is active. */
	private final class KernelJobContext implements JobContext {
		@Override public void operatorMessage(String m) { bootLog.add(m); }
		@Override public String readRecord(String dd) { return null; }
		@Override public void writeRecord(String dd, String r) { writeListing(r); }
	}

	/**
	 * Per-step job context. Walks forward through each inline DD
	 * dataset on successive {@link #readRecord} calls.
	 */
	private final class CursorContext implements JobContext {
		private final Job.Step step;
		private final Map<String, Integer> positions = new HashMap<>();

		CursorContext(Job.Step step) { this.step = step; }

		@Override
		public String readRecord(String ddName) {
			Job.Dd dd = step.dds().get(ddName.toUpperCase(Locale.ROOT));
			if (dd == null) return null;
			if (dd.kind() != Job.Dd.Kind.INLINE) return null;

			int pos = positions.getOrDefault(ddName, 0);
			if (pos >= dd.inline().size()) return null;
			positions.put(ddName, pos + 1);
			return dd.inline().get(pos);
		}

		@Override
		public void writeRecord(String ddName, String record) {
			writeListing(record);
		}

		@Override
		public void operatorMessage(String message) {
			bootLog.add(message);
		}
	}
}
