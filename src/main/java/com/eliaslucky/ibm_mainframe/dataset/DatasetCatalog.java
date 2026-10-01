package com.eliaslucky.ibm_mainframe.dataset;

import com.eliaslucky.ibm_mainframe.blocks.DiskDriveBlockEntity;
import com.eliaslucky.mc_dos.blocks.computer.VirtualFileSystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The catalog of datasets known to one mainframe.
 *
 * <p>Aggregates two kinds of volumes:
 * <ul>
 *	 <li><b>SYSRES</b> — the system residence volume, always mounted.
 *		 Backed by {@code /SYS1/DATASETS/} in the VFS. Datasets here
 *		 persist regardless of any disk drive state.</li>
 *	 <li><b>Mounted packs</b> — every disk pack currently inserted in
 *		 a 2311. Backed by the drive's own NBT. Datasets on a pack
 *		 are only visible while the pack is mounted.</li>
 * </ul>
 *
 * <p>When a DSN is looked up, SYSRES is checked first, then every
 * mounted pack in unit order. The first match wins.
 *
 * <p>Temp datasets ({@code &&name}) live only in memory and are
 * discarded when the job ends.
 */
public final class DatasetCatalog {
	private static final String SYS1_DIR	 = "/SYS1";
	private static final String DATASETS_DIR = "/SYS1/DATASETS";

	private final VirtualFileSystem vfs;
	private final Supplier<List<DiskDriveBlockEntity>> drives;

	/** In-memory cache of SYSRES datasets, keyed by DSN. */
	private final Map<String, Dataset> sysresCache = new LinkedHashMap<>();
	private final Map<String, Dataset> temporary   = new LinkedHashMap<>();

	public DatasetCatalog(VirtualFileSystem vfs, Supplier<List<DiskDriveBlockEntity>> drives) {
		this.vfs = vfs;
		this.drives = drives;
		ensureDirectories();
	}

	private void ensureDirectories() {
		if (vfs.resolvePath(SYS1_DIR) == null)	   vfs.createDirectory(SYS1_DIR);
		if (vfs.resolvePath(DATASETS_DIR) == null) vfs.createDirectory(DATASETS_DIR);
	}

	// --- Lookup ----------------------------------------------------------

	public Optional<Dataset> lookup(String dsn) {
		if (dsn == null) return Optional.empty();
		String up = dsn.toUpperCase(Locale.ROOT);

		if (up.startsWith("&&")) {
			return Optional.ofNullable(temporary.get(up));
		}

		Dataset sysres = sysresLookup(up);
		if (sysres != null) return Optional.of(sysres);

		for (DiskDriveBlockEntity drive : drives.get()) {
			if (!drive.hasPack()) continue;
			Dataset pd = drive.readDataset(up);
			if (pd != null) return Optional.of(pd);
		}
		return Optional.empty();
	}

	public boolean exists(String dsn) { return lookup(dsn).isPresent(); }

	private Dataset sysresLookup(String dsn) {
		if (sysresCache.containsKey(dsn)) return sysresCache.get(dsn);

		String path = DATASETS_DIR + "/" + dsn;
		VirtualFileSystem.Node node = vfs.resolvePath(path);
		if (node == null || node.isDirectory) return null;

		Dataset.Descriptor desc = new Dataset.Descriptor(
				dsn,
				Dataset.RecordFormat.F, 80, 0,
				Dataset.Dsorg.PS, "SYSRES", 0);
		Dataset ds = new Dataset(desc);
		ds.loadRecords(node.content);
		sysresCache.put(dsn, ds);
		return ds;
	}

	// --- Allocation ------------------------------------------------------

	/**
	 * Create a dataset. If the descriptor names a mounted volume, the
	 * dataset is stored on that pack; otherwise it goes to SYSRES.
	 */
	public Dataset createPermanent(String dsn, Dataset.Descriptor desc) {
		String up = dsn.toUpperCase(Locale.ROOT);
		String volser = desc.volumeSerial() == null ? "" : desc.volumeSerial();

		if (!volser.isEmpty() && !"SYSRES".equalsIgnoreCase(volser)) {
			for (DiskDriveBlockEntity drive : drives.get()) {
				if (drive.hasPack() && drive.volumeSerial().equalsIgnoreCase(volser)) {
					Dataset ds = new Dataset(new Dataset.Descriptor(
							up, desc.recfm(), desc.lrecl(), desc.blksize(),
							desc.dsorg(), volser, System.currentTimeMillis()));
					drive.writeDataset(ds);
					return ds;
				}
			}
			// Requested volume not mounted — fall through to SYSRES.
		}

		Dataset ds = new Dataset(new Dataset.Descriptor(
				up, desc.recfm(), desc.lrecl(), desc.blksize(),
				desc.dsorg(), "SYSRES", System.currentTimeMillis()));
		sysresCache.put(up, ds);
		return ds;
	}

	public Optional<Dataset> createTemp(String dsn, int lrecl) {
		String up = dsn.toUpperCase(Locale.ROOT);
		Dataset.Descriptor desc = new Dataset.Descriptor(
				up, Dataset.RecordFormat.F, lrecl, 0,
				Dataset.Dsorg.PS, "&&TEMP", System.currentTimeMillis());
		Dataset ds = new Dataset(desc);
		temporary.put(up, ds);
		return Optional.of(ds);
	}

	public boolean delete(String dsn) {
		if (dsn == null) return false;
		String up = dsn.toUpperCase(Locale.ROOT);

		if (up.startsWith("&&")) return temporary.remove(up) != null;

		if (sysresCache.remove(up) != null) {
			String path = DATASETS_DIR + "/" + up;
			if (vfs.resolvePath(path) != null) vfs.deleteFile(path);
			return true;
		}

		for (DiskDriveBlockEntity drive : drives.get()) {
			if (drive.hasPack() && drive.deleteDataset(up)) return true;
		}
		return false;
	}

	// --- Persistence -----------------------------------------------------

	/**
	 * Flush every SYSRES dataset to the VFS. Datasets on mounted packs
	 * are persisted by the drive's own {@code setChanged()}; nothing
	 * to do for those here.
	 */
	public void flush() {
		for (Map.Entry<String, Dataset> e : sysresCache.entrySet()) {
			String path = DATASETS_DIR + "/" + e.getKey();
			vfs.writeFile(path, e.getValue().serializeRecords());
		}
	}

	/** Discard all temp datasets. Called at end of job. */
	public void clearTemp() { temporary.clear(); }

	// --- Listing ---------------------------------------------------------

	/**
	 * Every dataset known to the catalog: SYSRES entries, mounted
	 * pack entries, and temps. Duplicates (same DSN on two volumes)
	 * are not deduplicated — the caller can filter if needed.
	 */
	public List<String> listAll() {
		List<String> out = new ArrayList<>(sysresCache.keySet());
		for (DiskDriveBlockEntity drive : drives.get()) {
			if (drive.hasPack()) out.addAll(drive.datasetNames());
		}
		out.addAll(temporary.keySet());
		return out;
	}
}
