package com.eliaslucky.ibm_mainframe.jcl;

import java.util.List;
import java.util.Map;

/**
 * One parsed job. A deck may contain several jobs; each ends with a
 * bare {@code //} card.
 *
 * @param name	the job name from the JOB card (1–8 chars)
 * @param steps ordered EXEC steps
 */
public record Job(String name, List<Step> steps) {
	/**
	 * One EXEC step.
	 *
	 * @param name	  the step name from the {@code //NAME EXEC ...} card
	 * @param program the program to invoke, e.g. {@code "FORT"}
	 * @param dds	  DD statements by DDNAME, uppercase
	 */
	public record Step(String name, String program, Map<String, Dd> dds) {}

	/**
	 * One DD statement.
	 *
	 * @param ddName	the DDNAME, uppercase
	 * @param kind		what the statement refers to
	 * @param dataset	the dataset name for {@link Kind#DATASET}, else null
	 * @param sysout	the SYSOUT class for {@link Kind#SYSOUT}, else null
	 * @param inline	the inline data cards for {@link Kind#INLINE}, else null
	 * @param disp          disposition, or null if not specified
	 * @param unit          unit name, e.g. {@code "TAPE"}, {@code "SYSDA"}, or null
	 */
	public record Dd(String ddName, Kind kind, String dataset, String sysout, List<String> inline, Disp disp, String unit) {
		public enum Kind {
			/** {@code DD *} — data cards follow in the deck. */
			INLINE,
			/** {@code DD DSN=...} — a catalogued dataset. */
			DATASET,
			/** {@code DD SYSOUT=...} — job listing output. */
			SYSOUT,
			/** {@code DD DUMMY} or unparsed. */
			OTHER
		}

		/**
		 * Dataset disposition. {@code status} is NEW/OLD/SHR/MOD;
		 * {@code normalDisposition} is KEEP/DELETE/CATLG/PASS
		 */
		public record Disp(String status, String normalDisposition) {
			public static final Disp OLD = new Disp("OLD", "KEEP");
			public static final Disp SHR = new Disp("SHR", "KEEP");
			public static final Disp NEW = new Disp("NEW", "KEEP");
			public static final Disp MOD = new Disp("MOD", "KEEP");
			public static final Disp TEMP = new Disp("NEW", "DELETE");

			/**
			 * Parse the JCL DISP= value. Accepts both bare and parenthesized forms.
			 */
			public static Disp parse(String raw) {
				if (raw == null || raw.isEmpty()) return null;
				String s = raw.trim();
				if (s.startsWith("(") && s.endsWith(")")) s = s.substring(1,s.length()-1);
				String[] parts = s.split(",",-1);
				String status = parts.length > 0 && !parts[0].isEmpty()
					? parts[0].trim().toUpperCase(Locale.ROOT) : "OLD";
				String normal = parts.length > 1 && !parts[1].isEmpty()
					? parts[1].trim().toUpperCase(Locale.ROOT) : defaultNormal(status);
				return new Disp(status, normal);
			}
			private static String defaultNormal(String status) {
				return switch (status) {
					case "NEW" -> "DELETE";
					default -> "KEEP";
				}
			}

			public boolean isNew()    { return "NEW".equals(status); }
			public boolean isOld()    { return "OLD".equals(status); }
			public boolean isShared() { return "SHR".equals(status); }
			public boolean isMod()    { return "MOD".equals(status); }
			public boolean deleteOnEnd() { return "DELETE".equals(normalDisposition); }
		}
	}
}
