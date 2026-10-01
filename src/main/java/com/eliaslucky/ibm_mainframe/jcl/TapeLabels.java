package com.eliaslucky.ibm_mainframe.jcl;

/**
 * Standard OS/360 volume and file label records for magnetic tape.
 *
 * <p>Format is a simplification of the real 80-byte records. The
 * record type is the first 4 characters; the fields this mod tracks
 * follow in a fixed layout. Real labels also carry a file serial,
 * generation number, creation date, expiry date, block count, and
 * system code — none of which change behavior for the mod's purposes.
 *
 * Tape layout with standard labels
 * <pre>
 *	 File 0: [VOL1 &lt;volume serial&gt;]						 then tape mark
 *	 File N: [HDR1 &lt;dsn&gt; &lt;volser&gt; &lt;file#&gt;] [data...] [EOF1 ...] then tape mark
 * </pre>
 *
 * <p>The kernel's tape DD handling skips VOL1 and HDR1 transparently
 * and treats EOF1 as end-of-data, so a running program never sees
 * labels. Programs that need raw access use {@code LABEL=NL} on the
 * DD statement.
 */
public final class TapeLabels {
	public static final int RECORD_LEN = 80;

	private TapeLabels() {}

	// --- Builders --------------------------------------------------------

	public static String vol1(String volumeSerial) {
		return pad("VOL1" + trunc(volumeSerial, 6), RECORD_LEN);
	}

	public static String hdr1(String dsn, String volumeSerial, int fileNum) {
		return pad(String.format("HDR1%-17s%-6s%04d",
				trunc(dsn, 17), trunc(volumeSerial, 6), fileNum), RECORD_LEN);
	}

	public static String eof1(String dsn, String volumeSerial, int fileNum) {
		return pad(String.format("EOF1%-17s%-6s%04d",
				trunc(dsn, 17), trunc(volumeSerial, 6), fileNum), RECORD_LEN);
	}

	// --- Parsers ---------------------------------------------------------

	public static boolean isVol1(String r) { return r != null && r.startsWith("VOL1"); }
	public static boolean isHdr1(String r) { return r != null && r.startsWith("HDR1"); }
	public static boolean isEof1(String r) { return r != null && r.startsWith("EOF1"); }

	public static boolean isAnyLabel(String r) {
		return isVol1(r) || isHdr1(r) || isEof1(r);
	}

	public static String volumeSerialFrom(String label) {
		if (label == null || label.length() < 10) return "";
		return label.substring(4, 10).trim();
	}

	public static String dsnFrom(String label) {
		if (label == null || label.length() < 21) return "";
		return label.substring(4, 21).trim();
	}

	public static int fileNumberFrom(String label) {
		if (label == null || label.length() < 25) return 0;
		try { return Integer.parseInt(label.substring(21, 25).trim()); }
		catch (NumberFormatException e) { return 0; }
	}

	// --- Helpers ---------------------------------------------------------

	private static String pad(String s, int len) {
		if (s.length() >= len) return s.substring(0, len);
		StringBuilder sb = new StringBuilder(len).append(s);
		while (sb.length() < len) sb.append(' ');
		return sb.toString();
	}

	private static String trunc(String s, int len) {
		if (s == null) return "";
		return s.length() > len ? s.substring(0, len) : s;
	}
}
