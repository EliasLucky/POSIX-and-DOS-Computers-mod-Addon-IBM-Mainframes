package com.eliaslucky.ibm_mainframe.blocks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The IBM 1052 Printer-Keyboard.
 *
 * <p>The standard console for the System/360 Model 30 and for the
 * earliest System/370s (Models 155 and 165). A Selectric typewriter
 * mechanism prints on continuous fan-fold paper. No CRT; no phosphor;
 * no blinking cursor.
 *
 * <p>Client-side rendering is the paper-style screen in
 * {@link com.eliaslucky.ibm_mainframe.client.PrinterConsoleScreen}.
 */
public class Console1052Block extends AbstractConsoleBlock {
	public Console1052Block(Properties p) { super(p); }

	@Override
	protected void openScreenClient(BlockPos pos) {
		Minecraft.getInstance().setScreen(new PrinterConsoleScreen(pos));
	}
}
