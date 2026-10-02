package com.eliaslucky.ibm_mainframe.jcl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses an OS/360-style job stream from a flat list of 80-column
 * card images. Sequence numbers in columns 73–80 are ignored.
 *
 * <p>Recognized syntax:
 * <ul>
 *	 <li>{@code //NAME JOB ...} starts a job</li>
 *	 <li>{@code //NAME EXEC PGM=prog} starts a step</li>
 *	 <li>{@code //DDNAME DD ...} declares a dataset for the step</li>
 *	 <li>{@code //DDNAME DD *} followed by data cards and {@code /*}</li>
 *	 <li>{@code //} alone ends the job</li>
 *	 <li>{@code //*} is a comment; the whole card is ignored</li>
 * </ul>
 *
 * <p>Not yet supported: multi-card continuation (a JCL statement
 * ending in a comma to continue on the next card), catalogued
 * procedures ({@code EXEC MYPROC}), and quoted values containing
 * embedded spaces. All three are rare in hand-written decks and can
 * be added when a specific need appears.
 *
 * <p>Errors are collected rather than thrown: a malformed job is
 * still produced with whatever could be understood, so the operator
 * sees the failure in the listing rather than a crash.
 */
public final class JclParser {
	private JclParser() {}

	/** Result of a parse: jobs found plus warnings. */
	public record Result(List<Job> jobs, List<String> warnings) {}

	/** Internal: a parsed job plus the index of the next card to read. */
	private record JobParse(Job job, int nextIndex) {}

	public static Result parse(List<String> cards) {
		List<Job> jobs = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		int i = 0;
		while (i < cards.size()) {
			String card = cards.get(i);

			// Skip blank lines, comments, and non-JCL cards until the
			// next JOB statement.
			if (!isJcl(card) || isComment(card) || !hasKeyword(card, "JOB")) {
				i++;
				continue;
			}

			JobParse jp = parseJob(cards, i, warnings);
			if (jp == null) {
				i++;
				continue;
			}
			jobs.add(jp.job());
			i = jp.nextIndex();
		}
		return new Result(jobs, warnings);
	}

	// --- Internals ---------------------------------------------------------

	private static JobParse parseJob(List<String> cards, int start, List<String> warnings) {
		String jobCard = cards.get(start);
		String jobName = jobNameFrom(jobCard);
		if (jobName == null) {
			warnings.add("Malformed JOB card: " + jobCard);
			return null;
		}

		List<Job.Step> steps = new ArrayList<>();
		int i = start + 1;

		while (i < cards.size()) {
			String card = cards.get(i);

			// End of job.
			if (isJcl(card) && card.trim().equals("//")) {
				i++;
				break;
			}
			// Skip comments and blank cards.
			if (isComment(card) || card.isBlank()) { i++; continue; }
			// Non-JCL cards between statements are stray; skip them.
			if (!isJcl(card)) { i++; continue; }

			if (hasKeyword(card, "EXEC")) {
				StepParse sp = parseStep(cards, i, warnings);
				if (sp != null) {
					steps.add(sp.step());
					i = sp.nextIndex();
				} else {
					i++;
				}
				continue;
			}

			i++;
		}

		return new JobParse(new Job(jobName, List.copyOf(steps)), i);
	}

	private record StepParse(Job.Step step, int nextIndex) {}

	private static StepParse parseStep(List<String> cards, int start, List<String> warnings) {
		String execCard = cards.get(start);
		String[] fields = splitJcl(execCard);
		if (fields.length < 2) {
			warnings.add("Malformed EXEC: " + execCard);
			return null;
		}

		String stepName = fields[0];
		String program = null;
		for (int f = 2; f < fields.length; f++) {
			if (fields[f].toUpperCase(Locale.ROOT).startsWith("PGM=")) {
				program = fields[f].substring(4).toUpperCase(Locale.ROOT);
			}
		}
		if (program == null) {
			warnings.add("EXEC without PGM=: " + execCard);
			return null;
		}

		Map<String, Job.Dd> dds = new LinkedHashMap<>();
		int i = start + 1;

		while (i < cards.size()) {
			String card = cards.get(i);

			if (isComment(card)) { i++; continue; }
			if (!isJcl(card)) break;
			if (card.trim().equals("//")) break;
			if (hasKeyword(card, "EXEC") || hasKeyword(card, "JOB")) break;

			if (hasKeyword(card, "DD")) {
				DdParse dp = parseDd(cards, i);
				if (dp != null) {
					dds.put(dp.dd().ddName(), dp.dd());
					i = dp.nextIndex();
					continue;
				}
			}
			i++;
		}

		Job.Step step = new Job.Step(stepName, program, Map.copyOf(dds));
		return new StepParse(step, i);
	}

	private record DdParse(Job.Dd dd, int nextIndex) {}

	private static DdParse parseDd(List<String> cards, int start) {
		String card = cards.get(start);
		String[] fields = splitJcl(card);
		if (fields.length < 2) return null;
		String ddName = fields[0].toUpperCase(Locale.ROOT);

		// Inline data block:  //DDNAME DD *  ...  /*
		for (int f = 2; f < fields.length; f++) {
			if (fields[f].equals("*")) {
				List<String> data = new ArrayList<>();
				int i = start + 1;
				while (i < cards.size()) {
					String c = cards.get(i);
					if (c.trim().equals("/*")) { i++; break; }
					data.add(c);
					i++;
				}
				return new DdParse(new Job.Dd(
						ddName, Job.Dd.Kind.INLINE,
						null, null, List.copyOf(data),
						Job.Dd.Disp.TEMP, null), i);
			}
		}

		String dataset = null, sysout = null, unit = null;
		Job.Dd.Disp disp = null;
		for (int f = 2; f < fields.length; f++) {
			String up = fields[f].toUpperCase(Locale.ROOT);
			if (up.startsWith("DSN="))	  dataset = fields[f].substring(4);
			if (up.startsWith("SYSOUT=")) sysout  = fields[f].substring(7);
			if (up.startsWith("UNIT="))   unit	  = fields[f].substring(5);
			if (up.startsWith("DISP="))   disp	  = Job.Dd.Disp.parse(fields[f].substring(5));
		}

		if (dataset != null) {
			return new DdParse(new Job.Dd(
					ddName, Job.Dd.Kind.DATASET, dataset, null, null,
					disp == null ? Job.Dd.Disp.OLD : disp, unit), start + 1);
		}
		if (sysout != null) {
			return new DdParse(new Job.Dd(
					ddName, Job.Dd.Kind.SYSOUT, null, sysout, null,
					null, unit), start + 1);
		}
		return new DdParse(new Job.Dd(
				ddName, Job.Dd.Kind.OTHER, null, null, null,
				disp, unit), start + 1);
	}

	// --- Card helpers ------------------------------------------------------

	/** A JCL card: {@code //} in columns 1–2. */
	private static boolean isJcl(String card) {
		return card.length() >= 2 && card.charAt(0) == '/' && card.charAt(1) == '/';
	}

	/** A JCL comment card: {@code //*} in columns 1–3. */
	private static boolean isComment(String card) {
		return card.length() >= 3
				&& card.charAt(0) == '/' && card.charAt(1) == '/'
				&& card.charAt(2) == '*';
	}

	private static boolean hasKeyword(String card, String kw) {
		String[] f = splitJcl(card);
		for (String s : f) if (s.equalsIgnoreCase(kw)) return true;
		return false;
	}

	private static String jobNameFrom(String card) {
		String[] f = splitJcl(card);
		return (f.length >= 1 && !f[0].isEmpty()) ? f[0] : null;
	}

	/**
	 * Split a JCL card on whitespace and commas, ignoring columns
	 * 73–80 and the leading {@code //}.
	 */
	private static String[] splitJcl(String card) {
		int end = Math.min(card.length(), 72);
		String s = card.substring(0, end);
		if (s.startsWith("//")) s = s.substring(2);
		s = s.trim();
		if (s.isEmpty()) return new String[0];
		return s.split("[\\s,]+");
	}
}
