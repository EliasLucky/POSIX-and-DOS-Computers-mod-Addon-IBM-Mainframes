package com.eliaslucky.ibm_mainframe.client;

import java.util.List;

/**
 * The subset of a console screen's API that server-side output
 * delivery uses. Both the 3270 display screen and the 1052 printer
 * screen implement this, so packets can update whichever one is
 * currently open without knowing which console model is in play.
 */
public interface ConsoleScreenBase {
	/** Replace the entire buffer with the given lines. */
	void setBuffer(List<String> incoming);

	/** Append one line to the buffer. */
	void appendLine(String line);
}
