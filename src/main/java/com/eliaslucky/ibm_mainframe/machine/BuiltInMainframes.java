package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.ibm_mainframe.channel.ChannelBus;
import com.eliaslucky.mc_dos.api.bios.Bios;
import com.eliaslucky.mc_dos.api.bios.MachineConfig;
import com.eliaslucky.mc_dos.api.hardware.PeripheralBus;
import com.eliaslucky.mc_dos.blocks.computer.processors.ICommandProcessor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The mainframe models shipped with this addon.
 *
 * <p>Mirrors the base mod's {@code ComputerType} pattern: an enum
 * whose values implement the machine interface. New models are
 * added as enum constants; addons add their own via
 * {@code MachineTypeRegistry}.
 *
 */
public enum BuiltInMainframes implements MainframeType {
	/**
	 * IBM System/360 Model 30. The entry-level machine of the 1964
	 * S/360 line. 64 KB of core memory, no floating point, card
	 * reader and printer through the channel. Ships with OS/360
	 * and FORTRAN IV.
	 */
	S360_MODEL_30(
			"ibm_mainframe:s360_model_30",
			"IBM System/360 Model 30",
			"IBM 2030 @ 40 KIPS",
			"OS/360 Release 21.8",
			"S/360 Channel",
			0x00FF66,
			Set.of("1052"),
			List.of(
					"",
					" IEA101A SPECIFY SYSTEM PARAMETERS OR PRESS ENTER FOR DEFAULT",
					" IEA102I INITIALIZING OS/360 RELEASE 21.8",
					" IEA103I AVAILABLE REGION: 54K",
					""
			)
	),

	// S370_MODEL_155(
	//		   "ibm_mainframe:s370_model_155",
	//		   "IBM System/370 Model 155",
	//		   "IBM 3155 @ 400 KIPS",
	//		   "OS/VS1 Release 6.0",
	//		   "S/370 Block Multiplexer",
	//		   0x00FF66,
	//		   Set.of("1052", "3210"),
	//		   List.of(
	//				   "",
	//				   " IEA101A SPECIFY SYSTEM PARAMETERS",
	//				   " IEA102I INITIALIZING OS/VS1 RELEASE 6.0",
	//				   ""
	//		   )
	// ),
	;

	private final String id;
	private final String modelName;
	private final String cpuName;
	private final String osVersion;
	private final String busType;
	private final int textColor;
	private final Set<String> acceptedConsoles;
	private final List<String> osBootLines;

	/** See {@link #commandProcessor()}. */
	private ICommandProcessor processor;

	BuiltInMainframes(String id,
					  String modelName,
					  String cpuName,
					  String osVersion,
					  String busType,
					  int textColor,
					  Set<String> acceptedConsoles,
					  List<String> osBootLines) {
		this.id = id;
		this.modelName = modelName;
		this.cpuName = cpuName;
		this.osVersion = osVersion;
		this.busType = busType;
		this.textColor = textColor;
		this.acceptedConsoles = acceptedConsoles;
		this.osBootLines = osBootLines;
	}

	// --- MachineType implementation ---------------------------------------

	@Override public String id()		{ return id; }
	@Override public String modelName() { return modelName; }
	@Override public String cpuName()	{ return cpuName; }
	@Override public String osVersion() { return osVersion; }
	@Override public String busType()	{ return busType; }
	@Override public int	textColor() { return textColor; }

	@Override public List<String> osBootLines() { return osBootLines; }
	@Override public List<String> defaultFiles() { return List.of(); }
	@Override public String defaultPath()		 { return "/"; }

	@Override public Bios bios() { return null; } // mainframes predate BIOS
	@Override public List<DriveBaySpec> driveBays() { return List.of(); }

	@Override
	public Supplier<MachineConfig> defaultConfig() {
		// Mainframes have no BIOS-configurable hardware; the record
		// is reused as a data container only.
		return () -> MachineConfig.ibmAt(System.currentTimeMillis());
	}

	/**
	 * The processor needs a back-reference to this enum constant, and the enum constant
	 * cannot reference itself in its own constructor arguments, so
	 * defer construction to first use.
	 */
	@Override
	public ICommandProcessor commandProcessor() {
		if (processor == null) processor = new MainframeCommandProcessor(this);
		return processor;
	}

	@Override
	public PeripheralBus createBus(Level level, BlockPos pos) {
		return new ChannelBus(level, pos);
	}

	// --- MainframeType implementation -------------------------------------

	@Override
	public Set<String> acceptedConsoles() { return acceptedConsoles; }
}
