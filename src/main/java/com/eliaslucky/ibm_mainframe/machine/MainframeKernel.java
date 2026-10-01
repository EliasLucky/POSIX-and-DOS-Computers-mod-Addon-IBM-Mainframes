package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.ibm_mainframe.channel.*;
import com.eliaslucky.ibm_mainframe.jcl.*;
import com.eliaslucky.ibm_mainframe.programs.FortProgram;
import com.eliaslucky.mc_dos.api.hardware.*;
import com.eliaslucky.mc_dos.blocks.computer.VirtualFileSystem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The mainframe kernel. Owns a device table, a boot log, and the
 * batch job runner.
 *
 * <p>Boot: scans the channel bus, registers every {@link ChannelDevice}
 * it finds under that device's {@link ChannelDevice#deviceName()}.
 *
 * <p>Running: {@link #runNextJob()} pulls cards from the registered
 * card reader, parses them as JCL, executes the steps, and routes
 * output through the DD statements to the printer.
 */
public class MainframeKernel implements Kernel {
	private final MainframeDeviceTable devices = new MainframeDeviceTable();
	private final List<String> bootLog = new ArrayList<>();
	private PeripheralBus bus;

	public MainframeKernel() {
		ProgramRegistry.register("FORT", new FortProgram());
	}

	@Override
	public void boot(PeripheralBus bus, VirtualFileSystem vfs) {
		this.bus = bus;
		devices.clear();
		bootLog.clear();

		List<PeripheralAddress> found = bus.scan();
		for (PeripheralAddress addr : found) {
			Peripheral p = bus.get(addr);
			if (p instanceof ChannelDevice dev) {
				if (devices.register(dev)) {
					bootLog.add("IEA900I " + String.format("%-8s", dev.deviceName())
							+ " " + dev.description());
				}
			}
		}
		if (devices.names().isEmpty()) {
			bootLog.add("IEA901I NO I/O DEVICES CONFIGURED");
		}
	}

	@Override public void shutdown() { devices.clear(); bootLog.clear(); bus = null; }
	@Override public List<String> getBootLog() { return List.copyOf(bootLog); }
	@Override public DeviceLookup getDevices() { return devices; }

	public MainframeDeviceTable deviceTable() { return devices; }

	/**
	 * Read the next job from the card reader and run it. Returns an
	 * operator-facing summary.
	 *
	 * <p>If there's no card reader or no deck, returns a message
	 * saying so. If the deck has multiple jobs, only the first is
	 * run; call again for the next.
	 */
	public String runNextJob() {
		ChannelDevice reader = devices.device("CARD-R");
		if (reader == null) {
			return "IEE600I NO CARD READER CONFIGURED";
		}

		// Phase 1: read all remaining cards off the reader, up to the
		// first bare "//" card. Anything after that is left for the
		// next invocation.
		List<String> cards = new ArrayList<>();
		boolean sawJobCard = false;
		while (true) {
			ChannelResult r = reader.execute(
					new ChannelCommand(ChannelCommand.Op.READ, 80));
			if (r.status() == ChannelDevice.ChannelStatus.UNIT_EXCEPTION) break; // EOF
			if (!r.success() || !r.hasData()) break;

			String card = new String(r.data(), StandardCharsets.US_ASCII);
			boolean jcl = card.length() >= 2
					&& card.charAt(0) == '/' && card.charAt(1) == '/';

			if (jcl && card.trim().equals("//")) {
				cards.add(card);
				break;	 // end of this job
			}
			if (jcl && card.contains(" JOB ")) sawJobCard = true;
			cards.add(card);
		}

		if (cards.isEmpty()) {
			return "IEE601I NO JOBS IN INPUT QUEUE";
		}
		if (!sawJobCard) {
			return "IEE602I INPUT DOES NOT BEGIN WITH A JOB CARD";
		}

		// Phase 2: parse and run.
		JclParser.Result parsed = JclParser.parse(cards);
		for (String w : parsed.warnings()) {
			writeToPrinter("SYSPRINT", "IEE700W " + w);
		}
		if (parsed.jobs().isEmpty()) {
			return "IEE603I JOB CARD REJECTED BY CONVERTER";
		}

		Job job = parsed.jobs().get(0);
		writeToPrinter("SYSPRINT", "");
		writeToPrinter("SYSPRINT",
				"IEF142I JOB " + job.name() + " STARTED");

		JobContext ctx = new KernelJobContext();
		for (Job.Step step : job.steps()) {
			executeStep(job, step, ctx);
		}

		writeToPrinter("SYSPRINT",
				"IEF142I JOB " + job.name() + " ENDED");
		return "IEE604I JOB " + job.name() + " COMPLETE";
	}

	// --- Step execution ----------------------------------------------------

	private void executeStep(Job job, Job.Step step, JobContext ctx) {
		ProgramRegistry.Program program = ProgramRegistry.get(step.program());
		if (program == null) {
			writeToPrinter("SYSPRINT",
					"IEF212I STEP " + step.name() + " -- PROGRAM "
							+ step.program() + " NOT FOUND");
			return;
		}
		writeToPrinter("SYSPRINT",
				"IEF142I STEP " + step.name() + " -- EXEC PGM=" + step.program());

		CursorContext cursor = new CursorContext(step, ctx);
		program.run(step, cursor);
	}

	// --- JobContext: routes DD name to physical device ---------------------

	/**
	 * Per-step job context. Holds a cursor into each inline dataset so
	 * successive {@code readRecord} calls walk forward through the DD.
	 */
	private final class CursorContext implements JobContext {
		private final Job.Step step;
		private final JobContext outer;
		private final Map<String, Integer> positions = new java.util.HashMap<>();

		CursorContext(Job.Step step, JobContext outer) {
			this.step = step;
			this.outer = outer;
		}

		@Override
		public String readRecord(String ddName) {
			Job.Dd dd = step.dds().get(ddName.toUpperCase(Locale.ROOT));
			if (dd == null) return null;

			switch (dd.kind()) {
				case INLINE -> {
					int pos = positions.getOrDefault(ddName, 0);
					if (pos >= dd.inline().size()) return null;
					positions.put(ddName, pos + 1);
					return dd.inline().get(pos);
				}
				case DATASET -> {
					// v1: no catalog. Report once and return EOF.
					return null;
				}
				case SYSOUT -> {
					return null;   // never readable
				}
				default -> { return null; }
			}
		}

		@Override
		public void writeRecord(String ddName, String record) {
			writeToPrinter(ddName, record);
		}

		@Override
		public void operatorMessage(String message) {
			bootLog.add(message);
		}
	}

	// --- Printer routing ---------------------------------------------------

	private void writeToPrinter(String ddName, String record) {
		ChannelDevice printer = devices.device("1403");
		if (printer == null) {
			// No printer: keep the message in the boot log so the
			// operator still sees something.
			// THIS IS A TEST AND NOT THE REAL LISTING.
			bootLog.add("[SYSPRINT] " + record);
			return;
		}
		byte[] bytes = (record + "\n").getBytes(StandardCharsets.US_ASCII);
		printer.execute(new ChannelCommand(
				ChannelCommand.Op.WRITE, bytes.length, false, bytes));
	}
}
