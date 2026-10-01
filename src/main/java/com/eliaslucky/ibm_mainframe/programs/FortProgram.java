package com.eliaslucky.ibm_mainframe.programs;

import com.eliaslucky.ibm_mainframe.jcl.Job;
import com.eliaslucky.ibm_mainframe.jcl.JobContext;
import com.eliaslucky.ibm_mainframe.jcl.ProgramRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * A very small subset of Fortran, packaged as a JCL program.
 *
 * <p>The card images are written to SYSPRINT first, prefixed by a
 * compiler banner, then the program's output is appended under a
 * {@code *** PROGRAM OUTPUT ***} separator.
 *
 * TODO: This small Fortrant compiler sucks but it's good for small testing
 */
public final class FortProgram implements ProgramRegistry.Program {
	private static final String BANNER =
			"FORT V1.0	--	IBM SYSTEM/360 FORTRAN IV  --  CARD IMAGE LISTING";

	@Override
	public void run(Job.Step step, JobContext ctx) {
		ctx.writeRecord("SYSPRINT", BANNER);
		ctx.writeRecord("SYSPRINT", repeat('-', BANNER.length()));
		ctx.writeRecord("SYSPRINT", "");

		// Phase 1: read the source cards into memory so we can list
		// them, then run them.
		List<String> source = new ArrayList<>();
		while (true) {
			String card = ctx.readRecord("SYSIN");
			if (card == null) break;
			source.add(card);
		}

		for (int i = 0; i < source.size(); i++) {
			String lineNumber = String.format("%05d", (i + 1) * 10);
			ctx.writeRecord("SYSPRINT", " " + lineNumber + " " + source.get(i));
		}
		ctx.writeRecord("SYSPRINT", "");
		ctx.writeRecord("SYSPRINT", "*** PROGRAM OUTPUT ***");
		ctx.writeRecord("SYSPRINT", "");

		// Phase 2: interpret the subset.
		int lineNo = 10;
		for (String raw : source) {
			String stmt = statementOf(raw);
			if (stmt.isEmpty()) { lineNo += 10; continue; }

			String upper = stmt.toUpperCase(java.util.Locale.ROOT);
			if (upper.startsWith("PRINT")) {
				String text = extractPrintText(stmt);
				if (text != null) ctx.writeRecord("SYSPRINT", " " + text);
				else ctx.writeRecord("SYSPRINT",
						"FORTRAN ERROR AT LINE " + lineNo + ": malformed PRINT");
			}
			else if (upper.equals("END") || upper.startsWith("END ")) {
				ctx.writeRecord("SYSPRINT", "");
				ctx.writeRecord("SYSPRINT",
						"IHC218I END OF PROGRAM  --  NO ERRORS");
				return;
			}
			else if (upper.startsWith("PROGRAM")) {
				// Declarations are absorbed into the listing above.
			}
			lineNo += 10;
		}
		ctx.writeRecord("SYSPRINT", "");
		ctx.writeRecord("SYSPRINT",
				"IHC218I END OF INPUT  --  PROGRAM NOT TERMINATED BY END");
	}

	/**
	 * Extract columns 7–72 of a card image. Columns 1–5 are the label,
	 * column 6 is continuation, columns 73–80 are sequence.
	 */
	private static String statementOf(String card) {
		if (card.length() < 7) return "";
		int end = Math.min(card.length(), 72);
		String stmt = card.substring(6, end);
		// A 'C' in column 1 is a comment card.
		if (!card.isEmpty() && (card.charAt(0) == 'C' || card.charAt(0) == '*')) return "";
		return stmt.trim();
	}

	/**
	 * Pull the literal text out of {@code PRINT *, 'text'} or
	 * {@code PRINT *, "text"}. Returns {@code null} if the statement
	 * doesn't match either shape.
	 */
	private static String extractPrintText(String stmt) {
		int q = -1;
		char quote = 0;
		for (int i = 0; i < stmt.length(); i++) {
			char c = stmt.charAt(i);
			if (c == '\'' || c == '"') { q = i; quote = c; break; }
		}
		if (q < 0) return null;
		int close = stmt.indexOf(quote, q + 1);
		if (close < 0) return null;
		return stmt.substring(q + 1, close);
	}

	private static String repeat(char c, int n) {
		StringBuilder sb = new StringBuilder(n);
		for (int i = 0; i < n; i++) sb.append(c);
		return sb.toString();
	}
}
