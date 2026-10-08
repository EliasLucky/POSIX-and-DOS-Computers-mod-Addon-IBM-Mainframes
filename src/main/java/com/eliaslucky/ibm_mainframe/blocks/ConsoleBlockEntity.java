package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.machine.MainframeKernel;
import com.eliaslucky.ibm_mainframe.AllBlockEntities;
import com.eliaslucky.mc_dos.blocks.computer.ComputerBlockEntity;
import com.eliaslucky.mc_dos.api.hardware.Kernel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The console's persistent state.
 *
 * <p>Owns the printed-output buffer. The kernel pushes lines here via
 * {@link #append(String)}; the screen reads them via {@link #getBuffer()}.
 * The buffer survives a save and reload, matching the physical model —
 * paper didn't unprint when the machine was powered down.
 *
 * <p>The binding to a CPU is resolved on demand: {@link #tryBind()}
 * scans the six neighbors for a {@link ComputerBlockEntity} whose
 * kernel is a {@link MainframeKernel}, and stores its position. The
 * reference is not persisted; it's re-derived on world load.
 */
public class ConsoleBlockEntity extends BlockEntity {
	private static final int MAX_BUFFER_LINES = 500;
	/** Server ticks between automatic re-bind attempts when unbound. */
	private static final int REBIND_INTERVAL = 40;

	private final List<String> buffer = new ArrayList<>();
	private BlockPos boundCpu = null;
	private int rebindCountdown = 0;

	public ConsoleBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.IBM_CONSOLE.get(), pos, state);
	}

	/**
	 * The model identifier for this console: {@code "1052"} or
	 * {@code "3270"}. Derived from the block class; not persisted
	 * because a console cannot change model in place.
	 *
	 * @return a short uppercase model tag, or {@code "UNKNOWN"} if the
	 *		   block is neither of the two known console variants
	 */
	public String consoleModel() {
		if (level == null) return "UNKNOWN";
		Block b = getBlockState().getBlock();
		if (b instanceof Console1052Block) return "1052";
		if (b instanceof Console3270Block) return "3270";
		return "UNKNOWN";
	}

	// --- Tick -------------------------------------------------------------

	/**
	 * Server-only. If unbound, retry periodically so a mainframe
	 * placed *after* the console is picked up without a manual
	 * right-click.
	 */
	public void tick() {
		if (level == null || level.isClientSide()) return;
		if (boundCpu != null) return;

		if (--rebindCountdown > 0) return;
		rebindCountdown = REBIND_INTERVAL;
		tryBind();
	}

	// --- Binding ---------------------------------------------------------

	/** Look for an adjacent mainframe and bind to it.*/
	public void tryBind() {
		if (level == null || level.isClientSide()) return;

		// Already bound to a live CPU?
		if (boundCpu != null) {
			BlockEntity cur = level.getBlockEntity(boundCpu);
			if (cur instanceof ComputerBlockEntity c && c.getKernel() instanceof MainframeKernel) {
				return;
			}
			boundCpu = null;
		}

		for (Direction d : Direction.values()) {
			BlockPos np = worldPosition.relative(d);
			BlockEntity be = level.getBlockEntity(np);
			if (be instanceof ComputerBlockEntity cpu && cpu.getKernel() instanceof MainframeKernel kernel) {
				ConsoleBlockEntity current = kernel.getConsole();
				if (current != null && current != this) {
					continue;
				}
				boundCpu = np;
				// Tell the kernel to route its output here.
				kernel.attachConsole(this);
				setChanged();
				return;
			}
		}
	}

	/** Detach from the currently bound CPU, if any. */
	public void unbind() {
		if (level == null || level.isClientSide()) { boundCpu = null; return; }
		if (boundCpu == null) return;

		if (boundCpu == null || level == null) return;
		BlockEntity cur = level.getBlockEntity(boundCpu);
		if (cur instanceof ComputerBlockEntity cpu&& cpu.getKernel() instanceof MainframeKernel k) {
			k.detachConsole(this);
		}
		boundCpu = null;
		setChanged();
	}
	
	/**
	 * One-way notification from the kernel: "another console has taken
	 * over, stop believing you are the operator display." Does not
	 * call back into the kernel — the kernel already replaced its
	 * reference.
	 */
	public void detachFromKernel() {
		boundCpu = null;
		setChanged();
	}

	public boolean isBound() { return boundCpu != null; }
	public BlockPos getBoundCpu() { return boundCpu; }

	// --- Output buffer ---------------------------------------------------

	public void append(String line) {
		buffer.add(line == null ? "" : line);
		while (buffer.size() > MAX_BUFFER_LINES) buffer.remove(0);
		setChanged();
		// Notify clients so an open screen refreshes.
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	public void appendLines(List<String> lines) {
		if (lines == null) return;
		for (String l : lines) append(l);
	}

	public void clear() {
		buffer.clear();
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	public List<String> getBuffer() { return List.copyOf(buffer); }

	// --- NBT -------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		ListTag list = new ListTag();
		for (String s : buffer) list.add(StringTag.valueOf(s));
		tag.put("Buffer", list);
		if (boundCpu != null) {
			tag.putLong("CpuPos", boundCpu.asLong());
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		buffer.clear();
		ListTag list = tag.getList("Buffer", Tag.TAG_STRING);
		for (int i = 0; i < list.size(); i++) buffer.add(list.getString(i));

		// Binding is re-derived, not trusted. Keep the position hint
		// in case the CPU is at the same coordinates after reload.
		boundCpu = tag.contains("CpuPos") ? BlockPos.of(tag.getLong("CpuPos")) : null;
	}

	/** Called on world load; the CPU may or may not still exist. */
	public void onLoad() {
		super.onLoad();
		tryBind();
	}
}
