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

	/** Tape motion states. Speeds are relative, not physical units. */
	public enum ReelState {
		IDLE,		 // no tape movement
		THREADING,	 // brief spin on insert to seat the tape
		READING,	 // normal read speed
		WRITING,	 // normal write speed
		SEARCHING,	 // fast forward through records
		REWINDING,	 // full reverse
		STOPPING	 // decelerating to idle
	}

	private ReelState reelState = ReelState.IDLE;
	private long  stateChangedMillis = 0L;
	private long  lastOpMillis		 = 0L;
	private long  lastTickMillis	 = 0L;
	private float currentSpeed		 = 0.0f;   // 0.0 .. ~4.5
	private float leftAngle			 = 0.0f;   // degrees, cumulative
	private float rightAngle		 = 0.0f;   // degrees, cumulative
	private float tapeProgress		 = 0.0f;   // 0.0 = start of tape, 1.0 = end

	/** Set on the client when a sync packet is received. Not persisted. */
	private transient long lastClientSyncMillis = 0L;

	/** Hub-to-full radius ratio. A 0.35 hub looks right for a 2401. */
	private static final float HUB_RATIO = 0.35f;

	/** Degrees per second per speed unit, at radius 1.0. */
	private static final float BASE_ANGULAR = 90.0f;

	/** Acceleration in speed units per second. Reaches full in ~0.5 s. */
	private static final float ACCEL = 4.0f;

	/** Progress per speed unit per second, for tape position. */
	private static final float PROGRESS_RATE = 0.05f;

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
		recomputeProgress();
		setReelState(ReelState.THREADING);
		lastOpMillis = System.currentTimeMillis();
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
		setReelState(ReelState.IDLE);
		currentSpeed = 0f;
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
		if (reelState != ReelState.READING && reelState != ReelState.SEARCHING) {
			setReelState(ReelState.READING);		
		}
		markOperation();
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
		if (reelState != ReelState.WRITING) {
			setReelState(ReelState.WRITING);
		}
		markOperation();
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
				setReelState(ReelState.REWINDING);
				markOperation();
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
				setReelState(ReelState.SEARCHING);
				markOperation();
				setChanged();
				yield ChannelResult.OK;
			}
			case ChannelControl.FORWARD_FILE -> {
				if (currentFile < files.size()) {
					currentFile++;
					currentRecord = 0;
				}
				setReelState(ReelState.SEARCHING);
				markOperation();
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

	// --- Reel state accessors -------------------------------------------

	public ReelState getReelState()  { return reelState; }
	public long		 getStateChangedMillis() { return stateChangedMillis; }
	public float	 getCurrentSpeed()		 { return currentSpeed; }
	public float	 getLeftAngle()			 { return leftAngle; }
	public float	 getRightAngle()		 { return rightAngle; }
	public float	 getTapeProgress()		 { return tapeProgress; }
	public long		 getLastClientSyncMillis() { return lastClientSyncMillis; }

	// --- State transitions ----------------------------------------------

	private void setReelState(ReelState s) {
		if (this.reelState == s) return;
		this.reelState = s;
		this.stateChangedMillis = System.currentTimeMillis();
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
		}
	}

	private void markOperation() {
		this.lastOpMillis = System.currentTimeMillis();
	}

	// --- Per-tick motion ------------------------------------------------

	/** Target speed for a state, in abstract units. */
	private static float targetSpeedFor(ReelState s) {
		return switch (s) {
			case IDLE, STOPPING		 -> 0.0f;
			case THREADING			 -> 0.3f;
			case READING, WRITING	 -> 1.0f;
			case SEARCHING			 -> 2.0f;
			case REWINDING			 -> 4.5f;
		};
	}

	/** Radius of a reel that holds fraction {@code p} of the tape. */
	private static float reelRadius(float p) {
		float h = HUB_RATIO;
		return (float) Math.sqrt(h * h + p * (1.0f - h * h));
	}

	/** Server-side tick. Called from the block's ticker once per game tick. */
	public void tick() {
		if (level == null || level.isClientSide()) return;

		long now = System.currentTimeMillis();
		if (lastTickMillis == 0L) { lastTickMillis = now; return; }
		float dt = (now - lastTickMillis) / 1000.0f;
		lastTickMillis = now;
		if (dt > 0.5f) dt = 0.5f;	// clamp after a stall

		// 1. Ramp speed toward the target.
		float target = targetSpeedFor(reelState);
		if (currentSpeed < target) {
			currentSpeed = Math.min(target, currentSpeed + ACCEL * dt);
		} else if (currentSpeed > target) {
			currentSpeed = Math.max(target, currentSpeed - ACCEL * dt);
		}

		// 2. Advance angles using omega = V / r.
		float rL = reelRadius(1.0f - tapeProgress);
		float rR = reelRadius(tapeProgress);
		float omegaL = BASE_ANGULAR * currentSpeed / rL;
		float omegaR = BASE_ANGULAR * currentSpeed / rR;
		leftAngle  += omegaL * dt;
		rightAngle -= omegaR * dt;

		// 3. Advance tape position for reading, writing, and rewinding.
		if (reelState == ReelState.READING || reelState == ReelState.WRITING) {
			tapeProgress += currentSpeed * dt * PROGRESS_RATE;
		} else if (reelState == ReelState.REWINDING) {
			tapeProgress -= currentSpeed * dt * PROGRESS_RATE * 3f;
		}
		tapeProgress = Math.max(0f, Math.min(1f, tapeProgress));

		// 4. Auto-transitions.
		if (reelState == ReelState.STOPPING && currentSpeed < 0.05f) {
			currentSpeed = 0f;
			setReelState(ReelState.IDLE);
		}
		if (reelState == ReelState.THREADING && now - stateChangedMillis > 1500) {
			setReelState(ReelState.STOPPING);
		}
		if ((reelState == ReelState.READING || reelState == ReelState.WRITING) && now - lastOpMillis > 1500) {
			setReelState(ReelState.STOPPING);
		}
		if (reelState == ReelState.SEARCHING && now - lastOpMillis > 400) {
			setReelState(ReelState.STOPPING);
		}
		if (reelState == ReelState.REWINDING && tapeProgress <= 0.001f) {
			setReelState(ReelState.STOPPING);
		}

		// 5. Periodic client sync. 4 Hz is enough - the client
		//	  extrapolates between packets using currentSpeed.
		if (level.getGameTime() % 5 == 0) {
			setChanged();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
		}
	}

	// --- Hook the state machine into existing operations ----------------

	private void recomputeProgress() {
		int total = 0;
		for (List<String> f : files) total += f.size();
		if (total == 0) { tapeProgress = 0f; return; }
		int consumed = 0;
		for (int i = 0; i < currentFile && i < files.size(); i++) {
			consumed += files.get(i).size();
		}
		consumed += currentRecord;
		tapeProgress = Math.min(1f, consumed / (float) total);
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

		tag.putString("ReelState", reelState.name());
		tag.putFloat("CurrentSpeed", currentSpeed);
		tag.putFloat("LeftAngle", leftAngle);
		tag.putFloat("RightAngle", rightAngle);
		tag.putFloat("TapeProgress", tapeProgress);
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

		try {
			reelState = ReelState.valueOf(tag.getString("ReelState"));
		} catch (IllegalArgumentException e) {
			reelState = ReelState.IDLE;
		}
		currentSpeed  = tag.getFloat("CurrentSpeed");
		leftAngle	  = tag.getFloat("LeftAngle");
		rightAngle	  = tag.getFloat("RightAngle");
		tapeProgress  = tag.getFloat("TapeProgress");

		if (level != null && level.isClientSide()) {
			lastClientSyncMillis = System.currentTimeMillis();
		}
	}
}
