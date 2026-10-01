package com.eliaslucky.ibm_mainframe.jcl;

/**
 * The I/O surface a running program uses. The kernel implements this
 * by routing each call through the channel bus to the appropriate
 * device. Programs never see the physical device; they only see DD
 * names.
 */
public interface JobContext {
	/**
	 * Read one 80-column record from the named DD. Returns {@code null}
	 * at end of data.
	 */
	String readRecord(String ddName);

	/**
	 * Write one 80-character or 132-character record to the named DD.
	 * The kernel handles the width for the device class.
	 */
	void writeRecord(String ddName, String record);

	/** Report a job-level diagnostic to the operator console. */
	void operatorMessage(String message);
}
