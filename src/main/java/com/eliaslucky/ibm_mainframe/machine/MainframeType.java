package com.eliaslucky.ibm_mainframe.machine;

import com.eliaslucky.mc_dos.blocks.computer.MachineType;

import java.util.Set;

/**
 * A mainframe machine type. Extends {@link MachineType} with
 * mainframe-specific properties that don't apply to PC-class
 * machines.
 *
 * <p>Implementations:
 * <ul>
 *	 <li>{@link BuiltInMainframes} - the models shipped with this addon.</li>
 *	 <li>Addons may implement this interface directly to add custom
 *		 mainframe models, then register them through
 *		 {@code MachineTypeRegistry}.</li>
 * </ul>
 */
public interface MainframeType extends MachineType {
	/**
	 * Model tags of console blocks that were standard for this
	 * machine. A console whose {@code consoleModel()} tag is in this
	 * set attaches with no warning. A console not in the set still
	 * attaches, but the kernel emits an anachronism warning on the
	 * boot log.
	 *
	 * <p>An empty set disables the warning entirely — useful for
	 * machines whose console history is unclear or for testing.
	 *
	 * @return a set of short model tags, e.g. {@code "1052"},
	 *		   {@code "3270"}; never {@code null}
	 */
	default Set<String> acceptedConsoles() { return Set.of(); }
}
