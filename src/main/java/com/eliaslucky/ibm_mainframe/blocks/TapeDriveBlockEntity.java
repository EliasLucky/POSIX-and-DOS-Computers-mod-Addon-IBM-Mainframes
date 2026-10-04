package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.AllBlockEntities;
import com.eliaslucky.ibm_mainframe.channel.*;
import com.eliaslucky.ibm_mainframe.items.MagneticTapeItem;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The IBM 2401. Executes CCWs against a mounted reel of tape.
 *
 * <p>Content model: a tape is a list of files; each file is a list of
 * records. The head sits at (currentFile, currentRecord). At the end
 * of a file, the head is at a tape mark; a READ there returns
 * UNIT_EXCEPTION. At the end of the last file, the tape is at
 * end-of-tape; a READ there also returns UNIT_EXCEPTION.
 *
 * <p>Writing at a position that isn't the end of the file truncates
 * everything from that point forward, then appends — matching how a
 * real tape behaves when you write mid-file.
 */
public class TapeDriveBlockEntity extends BlockEntity implements ChannelDevice {
	private String volumeSerial = "";
	private final List<List<String>> files = new ArrayList<>();
	private int currentFile = 0;
	private int currentRecord = 0;
	private boolean hasTape = false;

	public TapeDriveBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.TAPE_DRIVE.get(), pos, state);
	}

	// --- Media handling ---------------------------------------------------

	public boolean hasTape() { return hasTape; }

	public boolean insertTape(ItemStack stack) {
		if (hasTape) return false;
		if (!(stack.getItem() instanceof MagneticTapeItem)) return false;

		this.volumeSerial = MagneticTapeItem.getVolumeSerial(stack);
		this.files.clear();
		this.files.addAll(MagneticTapeItem.getFiles(stack));
		this.currentFile = 0;
		this.currentRecord = 0;
		this.hasTape = true;

		// Assign a serial if the reel was blank.
		if (this.volumeSerial.isEmpty()) {
			this.volumeSerial = "T" + Integer.toHexString(
					(int) (System.currentTimeMillis() & 0xFFFFF)).toUpperCase();
		}
		setChanged();
		return true;
	}

	public ItemStack ejectTape() {
		if (!hasTape) return ItemStack.EMPTY;
		ItemStack out = MagneticTapeItem.of(volumeSerial, files);
		hasTape = false;
		files.clear();
		volumeSerial = "";
		currentFile = 0;
		currentRecord = 0;
		setChanged();
		return out;
	}

	public String statusLine() {
		if (!hasTape) return "2401: no tape loaded.";
		return "2401: " + volumeSerial + " -- file " + (currentFile + 1)
				+ ", record " + currentRecord + ".";
	}

	public String getVolumeSerial() { return volumeSerial; }

	// --- ChannelDevice ----------------------------------------------------

	@Override public String deviceName() { return "TAPE"; }

	@Override
	public ChannelResult execute(ChannelCommand cmd) {
		if (!hasTape) return ChannelResult.NOT_READY;
		return switch (cmd.op()) {
			case READ	 -> readRecord();
			case WRITE	 -> writeRecord(cmd.payload());
			case SENSE	 -> ChannelResult.read(senseBytes());
			case CONTROL -> handleControl(cmd);
			case NOP	 -> ChannelResult.OK;
		};
	}

	private ChannelResult readRecord() {
		if (currentFile >= files.size()) return ChannelResult.UNIT_EXCEPTION;
		List<String> file = files.get(currentFile);
		if (currentRecord >= file.size()) return ChannelResult.UNIT_EXCEPTION;

		String rec = file.get(currentRecord);
		currentRecord++;
		setChanged();

		byte[] bytes = rec.getBytes(StandardCharsets.US_ASCII);
		if (bytes.length >= 80) return ChannelResult.read(bytes);
		byte[] padded = new byte[80];
		System.arraycopy(bytes, 0, padded, 0, bytes.length);
		Arrays.fill(padded, bytes.length, 80, (byte) ' ');
		return ChannelResult.read(padded);
	}

	private ChannelResult writeRecord(byte[] payload) {
		if (payload == null) return ChannelResult.REJECT;
		String rec = new String(payload, StandardCharsets.US_ASCII);

		while (files.size() <= currentFile) files.add(new ArrayList<>());
		List<String> file = files.get(currentFile);

		// Truncate from current position before appending.
		while (file.size() > currentRecord) file.remove(file.size() - 1);

		file.add(rec);
		currentRecord++;
		setChanged();
		return ChannelResult.OK;
	}

	private ChannelResult handleControl(ChannelCommand cmd) {
		byte[] p = cmd.payload();
		if (p == null || p.length == 0) return ChannelResult.REJECT;
		return switch (p[0]) {
			case ChannelControl.REWIND -> {
				currentFile = 0; currentRecord = 0;
				setChanged();
				yield ChannelResult.OK;
			}
			case ChannelControl.WEOF -> {
				currentFile++;
				currentRecord = 0;
				while (files.size() < currentFile) files.add(new ArrayList<>());
				setChanged();
				yield ChannelResult.OK;
			}
			case ChannelControl.FORWARD_SPACE -> {
				if (currentFile >= files.size()) yield ChannelResult.UNIT_EXCEPTION;
				List<String> file = files.get(currentFile);
				if (currentRecord < file.size()) {
					currentRecord++;
				} else {
					currentFile++;
					currentRecord = 0;
				}
				setChanged();
				yield ChannelResult.OK;
			}
			case ChannelControl.FORWARD_FILE -> {
				if (currentFile < files.size()) {
					currentFile++;
					currentRecord = 0;
				}
				setChanged();
				yield ChannelResult.OK;
			}
			case ChannelControl.EJECT -> ChannelResult.OK;	 // manual eject only
			default -> ChannelResult.REJECT;
		};
	}

	private byte[] senseBytes() {
		int s = 0;
		if (currentFile >= files.size()) s |= 0x01;						  // end of tape
		else if (currentRecord >= files.get(currentFile).size()) s |= 0x02; // tape mark
		return new byte[] { (byte) s };
	}

	// --- Peripheral compatibility ----------------------------------------

	@Override public String deviceClass() { return "tape"; }
	@Override public String vendorId()	  { return "ibm_mainframe"; }
	@Override public String productId()   { return "ibm_2401"; }
	@Override public String description() { return "IBM 2401 Magnetic Tape Unit"; }
	@Override public void write(byte[] d) { writeRecord(d); }
	@Override public byte[] read(int maxBytes) {
		ChannelResult r = readRecord();
		return r.hasData() ? r.data() : new byte[0];
	}
	@Override public int ioctl(int cmd, byte[] arg) { return -1; }
	@Override public boolean isReady() { return hasTape; }
	@Override public boolean hasData() { return false; }

	// --- NBT --------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("HasTape", hasTape);
		tag.putString("VolumeSerial", volumeSerial);
		tag.putInt("CurrentFile", currentFile);
		tag.putInt("CurrentRecord", currentRecord);

		ListTag filesTag = new ListTag();
		for (List<String> file : files) {
			ListTag f = new ListTag();
			for (String r : file) f.add(StringTag.valueOf(r));
			filesTag.add(f);
		}
		tag.put("Files", filesTag);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		hasTape = tag.getBoolean("HasTape");
		volumeSerial = tag.getString("VolumeSerial");
		currentFile = tag.getInt("CurrentFile");
		currentRecord = tag.getInt("CurrentRecord");

		files.clear();
		ListTag filesTag = tag.getList("Files", Tag.TAG_LIST);
		for (int i = 0; i < filesTag.size(); i++) {
			ListTag f = filesTag.getList(i);
			List<String> file = new ArrayList<>(f.size());
			for (int j = 0; j < f.size(); j++) file.add(f.getString(j));
			files.add(file);
		}
	}
}
