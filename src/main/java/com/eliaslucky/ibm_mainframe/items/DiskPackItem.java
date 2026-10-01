package com.eliaslucky.ibm_mainframe.items;

import com.eliaslucky.ibm_mainframe.dataset.Dataset;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An IBM 1316 disk pack. Non-stackable, holds the volume serial and
 * the datasets stored on it.
 *
 * <p>Structure: one volume serial plus a list of datasets. Each
 * dataset carries its own descriptor (RECFM, LRECL, DSORG, creation
 * time) — that descriptor is the disk equivalent of a tape's HDR1
 * record; there is no separate label to skip because the descriptor
 * is metadata, not a record in the data stream.
 *
 * <p>A blank pack can be crafted by right-clicking with an empty
 * hand; it gets a serial on first mount.
 */
public class DiskPackItem extends Item {
	public static final String TAG_VOLSER	 = "VolumeSerial";
	public static final String TAG_DATASETS  = "Datasets";
	public static final int MAX_VOLSER_LEN	 = 6;

	public DiskPackItem(Properties p) { super(p.stacksTo(1)); }

	// --- Construction ---------------------------------------------------

	public static ItemStack blank() {
		ItemStack s = new ItemStack(ModItems.DISK_PACK.get());
		CompoundTag t = s.getOrCreateTag();
		t.putString(TAG_VOLSER, "");
		t.put(TAG_DATASETS, new ListTag());
		return s;
	}

	public static ItemStack withSerial(String volser) {
		ItemStack s = blank();
		setVolumeSerial(s, volser);
		return s;
	}

	// --- Volume serial ---------------------------------------------------

	public static String getVolumeSerial(ItemStack s) {
		CompoundTag t = s.getTag();
		return t == null ? "" : t.getString(TAG_VOLSER);
	}

	public static void setVolumeSerial(ItemStack s, String volser) {
		String v = volser == null ? "" : volser.toUpperCase(Locale.ROOT);
		if (v.length() > MAX_VOLSER_LEN) v = v.substring(0, MAX_VOLSER_LEN);
		s.getOrCreateTag().putString(TAG_VOLSER, v);
	}

	// --- Dataset access --------------------------------------------------

	public static List<String> getDatasetNames(ItemStack s) {
		List<String> out = new ArrayList<>();
		CompoundTag t = s.getTag();
		if (t == null) return out;
		ListTag list = t.getList(TAG_DATASETS, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			out.add(list.getCompound(i).getString("Dsn"));
		}
		return out;
	}

	public static Dataset readDataset(ItemStack s, String dsn) {
		CompoundTag t = s.getTag();
		if (t == null) return null;
		String up = dsn.toUpperCase(Locale.ROOT);
		ListTag list = t.getList(TAG_DATASETS, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag ds = list.getCompound(i);
			if (ds.getString("Dsn").equalsIgnoreCase(up)) return deserialize(ds);
		}
		return null;
	}

	public static void writeDataset(ItemStack s, Dataset ds) {
		CompoundTag t = s.getOrCreateTag();
		ListTag list = t.getList(TAG_DATASETS, Tag.TAG_COMPOUND);
		String up = ds.descriptor().dsn().toUpperCase(Locale.ROOT);
		for (int i = 0; i < list.size(); i++) {
			if (list.getCompound(i).getString("Dsn").equalsIgnoreCase(up)) {
				list.set(i, serialize(ds));
				t.put(TAG_DATASETS, list);
				return;
			}
		}
		list.add(serialize(ds));
		t.put(TAG_DATASETS, list);
	}

	public static boolean deleteDataset(ItemStack s, String dsn) {
		CompoundTag t = s.getTag();
		if (t == null) return false;
		ListTag list = t.getList(TAG_DATASETS, Tag.TAG_COMPOUND);
		String up = dsn.toUpperCase(Locale.ROOT);
		for (int i = 0; i < list.size(); i++) {
			if (list.getCompound(i).getString("Dsn").equalsIgnoreCase(up)) {
				list.remove(i);
				t.put(TAG_DATASETS, list);
				return true;
			}
		}
		return false;
	}

	// --- NBT -------------------------------------------------------------

	private static CompoundTag serialize(Dataset ds) {
		CompoundTag t = new CompoundTag();
		t.putString("Dsn", ds.descriptor().dsn());
		t.putString("Recfm", ds.descriptor().recfm().name());
		t.putInt("Lrecl", ds.descriptor().lrecl());
		t.putInt("Blksize", ds.descriptor().blksize());
		t.putString("Dsorg", ds.descriptor().dsorg().name());
		t.putLong("Created", ds.descriptor().createdMillis());

		ListTag recs = new ListTag();
		for (String r : ds.records()) recs.add(StringTag.valueOf(r));
		t.put("Records", recs);
		return t;
	}

	private static Dataset deserialize(CompoundTag t) {
		Dataset.Descriptor d = new Dataset.Descriptor(
				t.getString("Dsn"),
				Dataset.RecordFormat.parse(t.getString("Recfm")),
				t.getInt("Lrecl"),
				t.getInt("Blksize"),
				Dataset.Dsorg.parse(t.getString("Dsorg")),
				"",		// volser lives on the pack, not the dataset
				t.getLong("Created"));
		Dataset ds = new Dataset(d);
		ListTag recs = t.getList("Records", Tag.TAG_STRING);
		for (int i = 0; i < recs.size(); i++) ds.addRecord(recs.getString(i));
		return ds;
	}
}
