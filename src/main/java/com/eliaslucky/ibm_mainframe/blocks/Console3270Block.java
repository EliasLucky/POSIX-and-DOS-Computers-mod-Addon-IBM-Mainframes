package com.eliaslucky.ibm_mainframe.blocks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The IBM 3270 Information Display Station.
 *
 * <p>The standard console for later System/370s and for the machines
 * that replaced them. A CRT screen with phosphor glow, scroll-back,
 * and a blinking cursor. The 3270 was introduced in 1971; it was
 * not a console for the System/360 Model 30, though it could be
 * attached as a regular terminal on machines that had a multiplexer
 * channel.
 *
 * <p>Client-side rendering is the green-screen console in
 * {@link com.eliaslucky.ibm_mainframe.client.ConsoleScreen}.
 */
public class Console3270Block extends AbstractConsoleBlock {
	public Console3270Block(Properties p) { super(p); }

	@Override
	protected void openScreenClient(BlockPos pos) {
		Minecraft.getInstance().setScreen(new ConsoleScreen(pos));
	}
}
