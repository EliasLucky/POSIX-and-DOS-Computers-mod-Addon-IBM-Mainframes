package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.ibm_mainframe.blocks.ConsoleBlockEntity;
import com.eliaslucky.ibm_mainframe.blocks.DiskDriveBlockEntity;
import com.eliaslucky.ibm_mainframe.blocks.TapeDriveBlockEntity;
import com.eliaslucky.ibm_mainframe.channel.ChannelBus;
import com.eliaslucky.ibm_mainframe.channel.ChannelCommand;
import com.eliaslucky.ibm_mainframe.channel.ChannelControl;
import com.eliaslucky.ibm_mainframe.channel.ChannelDevice;
import com.eliaslucky.ibm_mainframe.channel.ChannelNetwork;
import com.eliaslucky.ibm_mainframe.channel.ChannelResult;
import com.eliaslucky.ibm_mainframe.jcl.JclParser;
import com.eliaslucky.ibm_mainframe.jcl.Job;
import com.eliaslucky.ibm_mainframe.jcl.JobContext;
import com.eliaslucky.ibm_mainframe.jcl.ProgramRegistry;
import com.eliaslucky.ibm_mainframe.jcl.TapeLabels;
import com.eliaslucky.ibm_mainframe.programs.FortProgram;
import com.eliaslucky.ibm_mainframe.dataset.Dataset;
import com.eliaslucky.ibm_mainframe.dataset.DatasetCatalog;
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
import java.util.Set;

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
	 /** Cap on the pending-operator buffer while no console is attached. */
	private static final int MAX_PENDING_LINES = 1000;
	private final MainframeDeviceTable devices = new MainframeDeviceTable();
	private final Map<Role, Integer> roleToUnit = new EnumMap<>(Role.class);
	private final List<String> bootLog = new ArrayList<>();
	private final List<String> pendingOperator = new ArrayList<>();
	private final MainframeType type;
	
	private DatasetCatalog catalog;
	private boolean bootOk = false;
	private boolean warnedNoPrinter = false;
	private ConsoleBlockEntity console;

	public MainframeKernel() {
		this.type = type;
	}

	public MainframeType machineType() { return type; }

	// --- Kernel lifecycle -------------------------------------------------

	@Override
	public void boot(PeripheralBus bus, VirtualFileSystem vfs) {
		devices.clear();
		roleToUnit.clear();
		bootLog.clear();
		pendingOperator.clear();
		bootOk = false;
		warnedNoPrinter = false;

		if (!(bus instanceof ChannelBus cb)) {
			logBoot("IEA901E NO CHANNEL BUS AVAILABLE");
			return;
		}
		if (cb.multipleCpus()) {
			logBoot("IEA902E MULTIPLE PROCESSORS DETECTED ON CHANNEL");
			logBoot("IEA903E IPL FAILED -- SEPARATE THE CHANNEL NETWORKS");
			return;
		}

		ChannelNetwork net = cb.network();
		if (net.isEmpty()) {
			logBoot("IEA901E NO CHANNEL CABLES ATTACHED");
			return;
		}
		
		logBoot("IEA000I IBM SYSTEM/360 MODEL 30 -- IPL IN PROGRESS");

		int address = 0;
		for (ChannelDevice dev : net.devices()) {
			int unit = address++;

			if (!dev.isReady()) {
				bootLog.add("IEA904I UNIT " + unit + " " + dev.deviceName() + " NOT READY");
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
		this.catalog = new DatasetCatalog(vfs, this::allDiskDrives);

		if (roleToUnit.get(Role.SYSIN_READER) == null) {
			logBoot("IEE600W NO CARD READER -- START UNAVAILABLE");
		}
		if (roleToUnit.get(Role.SYSPRINT_PRINTER) == null) {
			logBoot("IEE605W NO PRINTER -- LISTINGS HELD TO CONSOLE");
			warnedNoPrinter = true;
		}
		
		logBoot("IEA001I IPL COMPLETE -- READY");

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
		pendingOperator.clear();
		catalog = null;
		bootOk = false;
	}

	@Override public List<String> getBootLog() { return List.copyOf(bootLog); }
	@Override public DeviceLookup getDevices() { return devices; }
	// --- Console binding --------------------------------------------------

	/**
	 * Called by a console BE when it discovers this kernel on an
	 * adjacent CPU. Flushes any pending operator output to the new
	 * console and routes all future messages there.
	 */
	public void attachConsole(ConsoleBlockEntity c) {
		if (c == null) return;
		// If a different console is already attached, detach it first.
		if (this.console != null && this.console != c) {
			this.console.detachFromKernel();
		}
		this.console = c;
		Set<String> accepted = type.acceptedConsoles();
		if (!accepted.isEmpty() && !accepted.contains(c.consoleModel())) {
			c.append("IEA910W " + c.consoleModel() + " NOT THE STANDARD CONSOLE FOR " + machineType.modelName());
			c.append("IEA911W CONSOLE WILL OPERATE WITH REDUCED FIDELITY");
		}
		
		c.append("IEE101I CONSOLE ATTACHED -- " + devices.names().size() + " DEVICES");

		if (!pendingOperator.isEmpty()) {
			c.appendLines(pendingOperator);
			pendingOperator.clear();
		}
	}

	/** Called by a console BE when it unbinds or is broken. */
	public void detachConsole(ConsoleBlockEntity c) {
		if (this.console == c) this.console = null;
	}

	public boolean hasConsole() { return console != null; }

	// --- Operator output --------------------------------------------------

	/**
	 * Route one line to the operator. Console if attached, else a
	 * bounded buffer that is flushed on attach.
	 */
	private void toOperator(String line) {
		if (line == null) return;
		if (console != null) {
			console.append(line);
		} else {
			pendingOperator.add(line);
			while (pendingOperator.size() > MAX_PENDING_LINES) {
				pendingOperator.remove(0);
			}
		}
	}

	/** Log a boot-time message: historical record + operator display. */
	private void logBoot(String line) {
		bootLog.add(line);
		toOperator(line);
	}

	/**
	 * Write one record to SYSPRINT. Routes to the printer if one is
	 * attached; otherwise prefixes the line and sends it to the
	 * operator console. Warns about the missing printer exactly once
	 * per boot.
	 */
	public void writeListing(String record) {
		ChannelDevice p = printer();
		if (p == null) {
			toOperator("[SYSPRINT] " + record);
			return;
		}
		byte[] bytes = (record + "\n").getBytes(StandardCharsets.US_ASCII);
		p.execute(new ChannelCommand(
				ChannelCommand.Op.WRITE, bytes.length, false, bytes));
	}

	/**
	 * Take and clear the pending-operator buffer. Used by the OUTPUT
	 * command when the console is not yet attached.
	 */
	public List<String> drainOrphanOutput() {
		List<String> out = List.copyOf(pendingOperator);
		pendingOperator.clear();
		return out;
	}

	public boolean hasOrphanOutput() { return !pendingOperator.isEmpty(); }
	/** Peek at the pending operator buffer without consuming it. */
	public List<String> peekOperatorBuffer() {
		return List.copyOf(pendingOperator);
	}

	/** One-line human-readable status summary. */
	public String statusLine() {
		StringBuilder sb = new StringBuilder();
		sb.append(bootOk ? "IPL COMPLETE" : "OFF");
		sb.append("  |	Devices: ").append(devices.all().size());
		sb.append("  Console: ").append(console != null ? "yes" : "no");
		sb.append("  Printer: ").append(printer() != null ? "yes" : "no");
		sb.append("  Reader: ").append(reader() != null ? "yes" : "no");
		return sb.toString();
	}
	// --- Accessors --------------------------------------------------------

	public MainframeDeviceTable deviceTable() { return devices; }
	public DatasetCatalog catalog() { return catalog; }
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

	/** Every disk drive on the channel, in unit order. */
	public List<DiskDriveBlockEntity> allDiskDrives() {
		List<DiskDriveBlockEntity> out = new ArrayList<>();
		for (MainframeDeviceTable.Entry e : devices.all()) {
			if (e.device() instanceof DiskDriveBlockEntity d) out.add(d);
		}
		return out;
	}

	/** Every tape drive on the channel, in unit order. */
	public List<ChannelDevice> allTapeDrives() {
		List<ChannelDevice> out = new ArrayList<>();
		for (MainframeDeviceTable.Entry e : devices.all()) {
			if ("tape".equals(e.device().deviceClass())) out.add(e.device());
		}
		return out;
	}

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
			ChannelResult res = r.execute(new ChannelCommand(ChannelCommand.Op.READ, 80));

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

		for (Job.Step step : job.steps()) {
			executeStep(step);
		}

		catalog.flush();
		catalog.clearTemp();
		writeListing("IEF142I JOB " + job.name() + " ENDED");
		return "IEE604I JOB " + job.name() + " COMPLETE";
	}

	private void executeStep(Job.Step step) {
		ProgramRegistry.Program program = ProgramRegistry.get(step.program());
		if (program == null) {
			writeListing("IEF212I STEP " + step.name() + " -- PROGRAM "
					+ step.program() + " NOT FOUND");
			return;
		}
		writeListing("IEF142I STEP " + step.name() + " -- EXEC PGM=" + step.program());

		CursorContext ctx = new CursorContext(step);
		try {
			program.run(step, ctx);
		}
		finally {
			ctx.closeTapes();
			catalog.flush();
		}
	}

	// --- JobContext implementations ---------------------------------------

	/**
	 * Per-step job context. Walks forward through each inline DD
	 * dataset on successive {@link #readRecord} calls.
	 */
	private final class CursorContext implements JobContext {
		private final Job.Step step;
		private final Map<String, Integer> readPositions = new HashMap<>();
		private final Map<String, Dataset> datasetByDd = new HashMap<>();
		private final Map<String, TapeState> tapeByDd = new HashMap<>();

		CursorContext(Job.Step step) {
			this.step = step;
			List<ChannelDevice> drivers = allTapeDrives();
			int tapeIndex = 0;

			for (Job.Dd dd : step.dds().values()) {
				String unit = dd.unit() == null ? "" : dd.unit().toUpperCase(Locale.ROOT);

				if (unit.startsWith("TAPE")) {
					if (tapeIndex < drivers.size()) {
						TapeState ts = new TapeState();
						ts.drive = drivers.get(tapeIndex++);
						ts.dsn = dd.dataset() != null ? dd.dataset() : "SCRATCH";
						tapeByDd.put(dd.ddName(),ts);
					}
					else {
						writeListing("IEF210W NO TAPE DRIVE AVAILABLE FOR " + dd.ddName());
					}
					continue;
				}

				if (dd.kind() == Job.Dd.Kind.DATASET && dd.dataset() != null) {
					String dsn = dd.dataset().toUpperCase(Locale.ROOT);
					var found = catalog.lookup(dsn);
					if (found.isPresent()) {
						Dataset ds = found.get();
						datasetByDd.put(dd.ddName(),ds);
						//DISP=MOD repositions the read cursor at end of file.
						if (dd.disp() != null && dd.disp().isMod()) {
							readPositions.put(dd.ddName(),ds.size());
						}
					}
					else if (dd.disp() != null && dd.disp().isNew()) {
						Dataset.Descriptor desc = new Dataset.Descriptor(
								dsn, Dataset.RecordFormat.FB, 80,0,
								Dataset.Dsorg.PS,
								dd.unit() == null ? "SYSRES" : dd.unit(),
								System.currentTimeMillis());
						datasetByDd.put(dd.ddName(), catalog.createPermanent(dsn,desc));
					}
					else {
						writeListing("IEF214W DATASET NOT FOUND: " + dsn);
					}
				}
			}
		}

		@Override
		public String readRecord(String ddName) {
			String key = ddName.toUpperCase(Locale.ROOT); 
			Job.Dd dd = step.dds().get(key);
			if (dd == null) return null;

			TapeState ts = tapeByDd.get(key);
			if (ts != null) return readTapeRecord(ts);

			Dataset ds = datasetByDd.get(key);
			if (ds != null) {
				int pos = readPositions.getOrDefault(key,0);
				if (pos >= ds.size()) return null;
				readPositions.put(key,pos+1);
				return ds.records().get(pos);
			}

			if (dd.kind() == Job.Dd.Kind.INLINE) {
				int pos = readPositions.getOrDefault(ddName, 0);
				if (pos >= dd.inline().size()) return null;
				readPositions.put(ddName, pos + 1);
				return dd.inline().get(pos);
			}
			return null;
		}

		private String readTapeRecord(TapeState ts) {
			// First read on this DD: skip any labels that precede data.
			if (!ts.opened) {
				ts.opened = true;
				while (true) {
					ChannelResult r = ts.drive.execute(
							new ChannelCommand(ChannelCommand.Op.READ, 80));
					if (r.status() == ChannelDevice.ChannelStatus.UNIT_EXCEPTION) return null;
					if (!r.hasData()) return null;
					String rec = new String(r.data(), StandardCharsets.US_ASCII);
					if (TapeLabels.isHdr1(rec)) continue;
					if (TapeLabels.isVol1(rec)) continue;
					if (TapeLabels.isEof1(rec)) return null;
					return rec;
				}
			}

			ChannelResult r = ts.drive.execute(
					new ChannelCommand(ChannelCommand.Op.READ, 80));
			if (r.status() == ChannelDevice.ChannelStatus.UNIT_EXCEPTION) return null;
			if (!r.hasData()) return null;
			String rec = new String(r.data(), StandardCharsets.US_ASCII);
			if (TapeLabels.isEof1(rec)) return null;
			return rec;
		}

		@Override
		public void writeRecord(String ddName, String record) {
			String key= ddName.toUpperCase(Locale.ROOT);
			Job.Dd dd = step.dds().get(key);

			if (dd == null || dd.kind() == Job.Dd.Kind.SYSOUT) {
				writeListing(record);
				return;
			}

			TapeState ts = tapeByDd.get(key);
			if (ts != null) { writeTapeRecord(ts,record); return; }

			Dataset ds = datasetByDd.get(key);
			if (ds != null) { ds.addRecord(record); return; }

			writeListing(record);
		}

		private void writeTapeRecord(TapeState ts, String record) {
			if (!ts.written) {
				ts.written = true;
				String hdr = TapeLabels.hdr1(ts.dsn, volserOf(ts), ts.fileNum);
				byte[] hb = hdr.getBytes(StandardCharsets.US_ASCII);
				ts.drive.execute(new ChannelCommand(
						ChannelCommand.Op.WRITE, hb.length, false, hb));
			}
			byte[] b = record.getBytes(StandardCharsets.US_ASCII);
			ts.drive.execute(new ChannelCommand( 
				ChannelCommand.Op.WRITE, b.length, false,b));
		}

		/** Close every written tape file with EOF1 and a tape mark. */
		void closeTapes() {
			for (TapeState ts : tapeByDd.values()) {
				if (!ts.written) continue;
				String eof = TapeLabels.eof1(ts.dsn, volserOf(ts), ts.fileNum);
				byte[] eb = eof.getBytes(StandardCharsets.US_ASCII);
				ts.drive.execute(new ChannelCommand(
						ChannelCommand.Op.WRITE, eb.length, false, eb));
				ts.drive.execute(new ChannelCommand(
						ChannelCommand.Op.CONTROL, 1, false, new byte[] { ChannelControl.WEOF }));
			}
		}

		private String volserOf(TapeState ts) {
			if (ts.drive instanceof TapeDriveBlockEntity td) return td.getVolumeSerial();
			return "";
		}

		@Override
		public void operatorMessage(String message) {
			bootLog.add(message);
			toOperator(message);
		}
	}

	private static final class TapeState {
		ChannelDevice drive;
		boolean opened;
		boolean written;
		int fileNum = 1;
		String dsn;
	}
}
