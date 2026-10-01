package com.eliaslucky.ibm_mainframe.jcl;

import java.util.HashMap;
import java.util.Map;

/**
 * Programs a mainframe kernel can invoke from a JCL {@code EXEC PGM=}
 * step. Distinct from the base mod's {@code ExecutableRegistry}, which
 * is "type a name, run a file" model. On a mainframe
 * the program name is a name, not a file path, and the program is
 * loaded by the kernel.
 */
public final class ProgramRegistry {
	/** The job-step execution surface a program sees. */
	@FunctionalInterface
	public interface Program {
		/**
		 * Run the program. Output goes through the context's writers.
		 *
		 * @param step the step being run (for DD lookup)
		 * @param ctx  the context providing read/write on DD names
		 */
		void run(Job.Step step, JobContext ctx);
	}

	private static final Map<String, Program> BY_NAME = new HashMap<>();

	private ProgramRegistry() {}

	public static void register(String name, Program p) {
		BY_NAME.put(name.toUpperCase(java.util.Locale.ROOT), p);
	}

	public static Program get(String name) {
		return BY_NAME.get(name.toUpperCase(java.util.Locale.ROOT));
	}
}
