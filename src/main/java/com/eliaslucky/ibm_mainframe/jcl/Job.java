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
	 */
	public record Dd(String ddName, Kind kind, String dataset, String sysout, List<String> inline) {
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
	}
}
