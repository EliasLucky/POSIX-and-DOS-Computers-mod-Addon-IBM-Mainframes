package com.eliaslucky.ibm_mainframe.dataset;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One sequential dataset (DSORG=PS) and its metadata.
 *
 * <p>Records are strings. Real OS/360 allowed binary records, but
 * every FORTRAN program of the era works in formatted text, and
 * card images are text. Binary datasets (object decks, SYSUT1) can
 * be added later by giving {@link Descriptor} a binary content flag.
 */
public final class Dataset {
	/** DCB attributes plus catalog bookkeeping. */
	public record Descriptor( String dsn, RecordFormat recfm, int lrecl, int blksize, Dsorg dsorg, String volumeSerial, long createdMillis) {
		public String toCatalogLine() {
			return String.join("|",
					dsn,
					recfm.name(),
					String.valueOf(lrecl),
					String.valueOf(blksize),
					dsorg.name(),
					volumeSerial == null ? "" : volumeSerial,
					String.valueOf(createdMillis));
		}

		public static Descriptor fromCatalogLine(String line) {
			String[] p = line.split("\\|", -1);
			if (p.length < 7) return null;
			try {
				return new Descriptor(
						p[0],
						RecordFormat.parse(p[1]),
						Integer.parseInt(p[2]),
						Integer.parseInt(p[3]),
						Dsorg.parse(p[4]),
						p[5].isEmpty() ? null : p[5],
						Long.parseLong(p[6]));
			} catch (NumberFormatException e) {
				return null;
			}
		}
	}

	/** OS/360 RECFM values this mod supports. */
	public enum RecordFormat {
		F,	 // fixed
		FB,  // fixed blocked
		V,	 // variable
		VB,  // variable blocked
		U;	 // undefined (raw)

		public static RecordFormat parse(String s) {
			if (s == null) return F;
			try { return valueOf(s.toUpperCase(Locale.ROOT)); }
			catch (IllegalArgumentException e) { return F; }
		}

		public boolean isFixed()   { return this == F || this == FB; }
		public boolean isBlocked() { return this == FB || this == VB; }
	}

	/** Dataset organization. Only PS is implemented in v1. */
	public enum Dsorg {
		PS,  // sequential
		PO,  // partitioned
		DA,  // direct access
		IS;  // indexed sequential

		public static Dsorg parse(String s) {
			if (s == null) return PS;
			try { return valueOf(s.toUpperCase(Locale.ROOT)); }
			catch (IllegalArgumentException e) { return PS; }
		}
	}

	// --- Instance ------------------------------------------------------

	private Descriptor descriptor;
	private final List<String> records = new ArrayList<>();

	public Dataset(Descriptor descriptor) { this.descriptor = descriptor; }

	public Descriptor descriptor()		{ return descriptor; }
	public void setDescriptor(Descriptor d) { this.descriptor = d; }
	public List<String> records()		{ return records; }
	public int size()					{ return records.size(); }

	public void addRecord(String r)		{ records.add(r == null ? "" : r); }
	public void setRecords(List<String> r) {
		records.clear();
		if (r != null) records.addAll(r);
	}

	/** Serialize records as one per line. Records cannot contain newlines. */
	public String serializeRecords() {
		return String.join("\n", records);
	}

	public void loadRecords(String content) {
		records.clear();
		if (content == null || content.isEmpty()) return;
		for (String line : content.split("\n", -1)) records.add(line);
	}
}
