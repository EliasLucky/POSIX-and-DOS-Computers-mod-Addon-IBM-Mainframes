package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.mc_dos.api.hardware.Kernel;
import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;
import com.eliaslucky.mc_dos.blocks.computer.fs.FileNamePolicy;
import com.eliaslucky.mc_dos.blocks.computer.fs.PosixFileNamePolicy;
import com.eliaslucky.mc_dos.blocks.computer.processors.ICommandProcessor;

/**
 * The interactive surface of a mainframe. Historically there was no
 * interactive shell — jobs came in through the card reader and
 * everything else was operator commands at the console. v1 keeps a
 * bare stub so the machine places and boots.
 */
public class MainframeCommandProcessor implements ICommandProcessor {
	@Override public String process(ComputerBlockEntity c, String in) { return ""; }
	@Override public String getPrompt(String path)		{ return ""; }
	@Override public String defaultPath()				{ return "/"; }
	@Override public FileNamePolicy fileNamePolicy()	{ return PosixFileNamePolicy.INSTANCE; }
	@Override public String osFamily()					{ return "os360"; }
	@Override public Kernel createKernel()				{ return new MainframeKernel(); }
}
