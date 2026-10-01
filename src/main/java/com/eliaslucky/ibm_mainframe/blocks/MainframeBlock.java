package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.mc_dos.blocks.computer.IBMComputerBlock;
import com.eliaslucky.ibm_mainframe.machine.MainframeType;

/**
 * The S/360 cabinet block. Everything else media, occupy, terminal
 * is inherited; the only thing that differs is the machine type,
 * whose {@code createBus} returns a channel bus.
 */
public class MainframeBlock extends IBMComputerBlock {
	public MainframeBlock(Properties properties) {
		super(properties, MainframeType.S360_MODEL_30);
	}
}
