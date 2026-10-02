package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.channel.*;
import com.eliaslucky.ibm_mainframe.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The IBM 1403 Line Printer. 132 columns per line. Everything a program
 * writes via WRITE CCWs is appended to a text buffer the player
 * collects with right-click.
 */
public class LinePrinterBlockEntity extends BlockEntity implements ChannelDevice {
	private final List<String> lines = new ArrayList<>();
	private int pageCount = 1;

	public LinePrinterBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.IBM_029_LINE_PRINTER.get(), pos, state);
	}

	// --- Output collection -------------------------------------------------

	public boolean hasOutput() { return !lines.isEmpty(); }

	/** Return everything printed so far and clear the buffer. */
	public String collectOutput() {
		String out = String.join("\n", lines);
		lines.clear();
		pageCount = 1;
		setChanged();
		return out;
	}

	public int lineCount() { return lines.size(); }

	// --- ChannelDevice -----------------------------------------------------

	@Override public String deviceName() { return "1403"; }

	@Override
	public ChannelResult execute(ChannelCommand cmd) {
		return switch (cmd.op()) {
			case WRITE -> writeBytes(cmd.payload());
			case CONTROL -> handleControl(cmd);
			case SENSE -> ChannelResult.read(new byte[] { 0 });
			case READ -> ChannelResult.REJECT;	 // printer doesn't read
			case NOP -> ChannelResult.OK;
		};
	}

	private ChannelResult writeBytes(byte[] payload) {
		if (payload == null || payload.length == 0) return ChannelResult.OK;
		String text = new String(payload, StandardCharsets.US_ASCII);
		// TODO: Printers didn't emit newlines in a WRITE the carriage control
		// character at the start of each record did that. Currently it accepts
		// raw newlines in the payload as line breaks.
		for (String raw : text.split("\n", -1)) {
			String line = raw.length() > 132 ? raw.substring(0, 132) : raw;
			lines.add(line);
		}
		setChanged();
		return ChannelResult.OK;
	}

	private ChannelResult handleControl(ChannelCommand cmd) {
		byte[] p = cmd.payload();
		if (p == null || p.length == 0) return ChannelResult.REJECT;
		return switch (p[0]) {
			case ChannelControl.FORM_FEED -> {
				lines.add("");	 // one blank line as a cheap form feed
				lines.add("");	 // and one more for visual separation
				setChanged();
				yield ChannelResult.OK;
			}
			case ChannelControl.CARRIAGE_RETURN -> {
				lines.add("");
				setChanged();
				yield ChannelResult.OK;
			}
			case ChannelControl.NEW_PAGE -> {
				pageCount++;
				lines.add("\fPage " + pageCount);
				setChanged();
				yield ChannelResult.OK;
			}
			default -> ChannelResult.REJECT;
		};
	}

	// --- Peripheral --------------------------------------------------------

	@Override public String deviceClass() { return "printer"; }
	@Override public String vendorId()	  { return "ibm_mainframe"; }
	@Override public String productId()   { return "ibm_1403"; }
	@Override public String description() { return "IBM 1403 Line Printer"; }
	@Override public void write(byte[] d) { writeBytes(d); }
	@Override public byte[] read(int maxBytes) { return new byte[0]; }
	@Override public int ioctl(int cmd, byte[] arg) { return -1; }
	@Override public boolean isReady() { return true; }
	@Override public boolean hasData() { return false; }

	// --- NBT ---------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		ListTag list = new ListTag();
		for (String l : lines) list.add(StringTag.valueOf(l));
		tag.put("Lines", list);
		tag.putInt("PageCount", pageCount);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		lines.clear();
		ListTag list = tag.getList("Lines", Tag.TAG_STRING);
		for (int i = 0; i < list.size(); i++) lines.add(list.getString(i));
		pageCount = tag.getInt("PageCount");
	}
}
