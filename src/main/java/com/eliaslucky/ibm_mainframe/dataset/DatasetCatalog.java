package com.eliaslucky.ibm_mainframe.dataset;

import com.eliaslucky.ibm_mainframe.dataset.Dataset.Descriptor;
import com.eliaslucky.mc_dos.blocks.computer.VirtualFileSystem;
import com.eliaslucky.mc_dos.blocks.computer.fs.FileOpResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The catalog of datasets known to one mainframe.
 *
 * Backed by two VFS locations:
 * <ul>
 *	 <li>{@code /SYS1/CATALOG} one line per dataset, holding the DCB
 *		 attributes. Format documented in {@link Descriptor#toCatalogLine}.</li>
 *	 <li>{@code /SYS1/DATASETS/<DSN>} one record per line. The DSN is
 *		 the filename verbatim (POSIX filename policy accepts dots).</li>
 * </ul>
 *
 * <p>Temp datasets ({@code &&name}) live only in memory and are
 * discarded when the job ends. Permanent datasets are flushed to
 * the VFS after each step.
 */
public final class DatasetCatalog {
	private static final String SYS1_DIR	 = "/SYS1";
	private static final String DATASETS_DIR = "/SYS1/DATASETS";
	private static final String CATALOG_PATH = "/SYS1/CATALOG";

	private final VirtualFileSystem vfs;
	private final Map<String, Dataset> permanent = new LinkedHashMap<>();
	private final Map<String, Dataset> temporary = new LinkedHashMap<>();

	public DatasetCatalog(VirtualFileSystem vfs) {
		this.vfs = vfs;
		ensureDirectories();
		loadCatalog();
	}

	// --- Directory setup ------------------------------------------------

	private void ensureDirectories() {
		if (vfs.resolvePath(SYS1_DIR) == null) vfs.createDirectory(SYS1_DIR);
		if (vfs.resolvePath(DATASETS_DIR) == null) vfs.createDirectory(DATASETS_DIR);
	}

	// --- Lookup ---------------------------------------------------------

	public Optional<Dataset> lookup(String dsn) {
		if (dsn == null) return Optional.empty();
		String up = dsn.toUpperCase(Locale.ROOT);
		if (up.startsWith("&&")) {
			return Optional.ofNullable(temporary.get(up));
		}
		return Optional.ofNullable(permanent.get(up));
	}

	public boolean exists(String dsn) {
		return lookup(dsn).isPresent();
	}

	// --- Allocation -----------------------------------------------------

	/** Create a permanent dataset. Overwrites any existing one. */
	public Dataset createPermanent(String dsn, Descriptor desc) {
		String up = dsn.toUpperCase(Locale.ROOT);
		Dataset d = new Dataset(desc);
		permanent.put(up, d);
		return d;
	}

	/** Create an in-memory temp dataset. Deleted at end of job. */
	public Dataset createTemp(String dsn, int lrecl) {
		String up = dsn.toUpperCase(Locale.ROOT);
		Descriptor desc = new Descriptor(up,
				Dataset.RecordFormat.F, lrecl, 0,
				Dataset.Dsorg.PS, "&&TEMP", System.currentTimeMillis());
		Dataset d = new Dataset(desc);
		temporary.put(up, d);
		return d;
	}

	public boolean delete(String dsn) {
		if (dsn == null) return false;
		String up = dsn.toUpperCase(Locale.ROOT);
		if (up.startsWith("&&")) {
			return temporary.remove(up) != null;
		}
		boolean removed = permanent.remove(up) != null;
		String path = DATASETS_DIR + "/" + up;
		if (vfs.resolvePath(path) != null) vfs.deleteFile(path);
		return removed;
	}

	// --- Persistence ----------------------------------------------------

	/** Write every permanent dataset and the catalog file back to the VFS. */
	public void flush() {
		StringBuilder sb = new StringBuilder();
		for (Dataset d : permanent.values()) {
			sb.append(d.descriptor().toCatalogLine()).append('\n');
		}
		vfs.writeFile(CATALOG_PATH, sb.toString());

		for (Dataset d : permanent.values()) {
			String path = DATASETS_DIR + "/" + d.descriptor().dsn();
			FileOpResult r = vfs.writeFile(path, d.serializeRecords());
			// Failure here means a broken VFS state; nothing actionable
			// from inside the catalog, so silently continue.
		}
	}

	private void loadCatalog() {
		permanent.clear();
		VirtualFileSystem.Node node = vfs.resolvePath(CATALOG_PATH);
		if (node == null || node.isDirectory) return;

		for (String line : node.content.split("\n", -1)) {
			if (line.isEmpty()) continue;
			Descriptor d = Descriptor.fromCatalogLine(line);
			if (d == null) continue;

			Dataset ds = new Dataset(d);
			VirtualFileSystem.Node data = vfs.resolvePath(DATASETS_DIR + "/" + d.dsn());
			if (data != null && !data.isDirectory) {
				ds.loadRecords(data.content);
			}
			permanent.put(d.dsn(), ds);
		}
	}

	/** Discard all temp datasets. Called at end of job. */
	public void clearTemp() { temporary.clear(); }

	// --- Listing --------------------------------------------------------

	public List<String> listPermanent() {
		return List.copyOf(permanent.keySet());
	}

	public List<String> listTemp() {
		return List.copyOf(temporary.keySet());
	}

	public List<String> listAll() {
		List<String> out = new ArrayList<>(permanent.keySet());
		out.addAll(temporary.keySet());
		return out;
	}
}
