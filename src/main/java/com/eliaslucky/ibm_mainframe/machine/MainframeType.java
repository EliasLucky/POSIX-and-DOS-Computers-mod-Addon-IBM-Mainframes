package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.mc_dos.api.bios.Bios;
import com.eliaslucky.mc_dos.api.bios.MachineConfig;
import com.eliaslucky.mc_dos.api.hardware.PeripheralBus;
import com.eliaslucky.mc_dos.blocks.computer.MachineType;
import com.eliaslucky.mc_dos.blocks.computer.processors.ICommandProcessor;
import com.eliaslucky.ibm_mainframe.channel.ChannelBus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Supplier;

/**
 * The IBM System/360 Model 30. A modest first mainframe: 64 KiB of
 * core storage, one integrated card reader/punch, one line printer.
 */
public final class MainframeType implements MachineType {
	public static final MainframeType S360_MODEL_30 = new MainframeType();

	private final ICommandProcessor processor = new MainframeCommandProcessor();

	private MainframeType() {}

	@Override public String id()		  { return "ibm_mainframe:s360_model_30"; }
	@Override public String modelName()   { return "IBM System/360 Model 30"; }
	@Override public String cpuName()	  { return "IBM 2030 @ 40 KIPS"; }
	@Override public String osVersion()   { return "OS/360 Release 21.8"; }
	@Override public String busType()	  { return "S/360 Channel"; }
	@Override public int	textColor()   { return 0x00FF66; }
	@Override public String defaultPath() { return "/"; }

	@Override public List<String> osBootLines() {
		return List.of(
			"",
			" IEA101A SPECIFY SYSTEM PARAMETERS OR PRESS ENTER FOR DEFAULT",
			" IEA102I INITIALIZING OS/360 RELEASE 21.8",
			" IEA103I AVAILABLE REGION: 54K",
			""
		);
	}

	@Override public List<String> defaultFiles() { return List.of(); }

	@Override public ICommandProcessor commandProcessor() { return processor; }

	/** No BIOS for a mainframe — it predates that concept. */
	@Override public Bios bios() { return null; }

	/** All I/O comes through the channel. No drive bays. */
	@Override public List<DriveBaySpec> driveBays() { return List.of(); }

	/**
	 * The machine config carries PC-era fields we don't use. Reusing
	 * the PC-AT default keeps serialization happy; the mainframe kernel
	 * ignores floppy/HD fields entirely.
	 */
	@Override public Supplier<MachineConfig> defaultConfig() {
		return () -> MachineConfig.ibmAt(System.currentTimeMillis());
	}

	/** The point of the whole feature: use the channel bus, not adjacency. */
	@Override public PeripheralBus createBus(Level level, BlockPos pos) {
		return new ChannelBus(level, pos);
	}
}
