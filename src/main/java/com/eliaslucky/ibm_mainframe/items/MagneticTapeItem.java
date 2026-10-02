package com.eliaslucky.ibm_mainframe.items;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import com.eliaslucky.ibm_mainframe.AllItems;

/**
 * A reel of 9-track magnetic tape. Non-stackable — each reel is a
 * unique physical volume.
 *
 * <p>Tape content is a list of tape files. Each file is a list of
 * records. A tape mark separates one file from the next; a program
 * reaches end-of-tape by running off the end of the last file.
 */
public class MagneticTapeItem extends Item {
	private static final String TAG_SERIAL = "VolumeSerial";
	private static final String TAG_FILES  = "Files";

	/** Maximum total records across all files on one reel. */
	public static final int MAX_RECORDS = 100_000;
	/** Records per file are capped to keep a single file bounded. */
	public static final int MAX_RECORDS_PER_FILE = 50_000;

	public MagneticTapeItem(Properties p) { super(p.stacksTo(1)); }

	// --- Construction ---------------------------------------------------

	/** A blank reel with no serial and no files. */
	public static ItemStack blank() {
		ItemStack s = new ItemStack(AllItems.MAGNETIC_TAPE.get());
		s.getOrCreateTag().putString(TAG_SERIAL, "");
		s.getOrCreateTag().put(TAG_FILES, new ListTag());
		return s;
	}

	public static ItemStack of(String serial, List<List<String>> files) {
		ItemStack s = new ItemStack(AllItems.MAGNETIC_TAPE.get());
		CompoundTag tag = s.getOrCreateTag();
		tag.putString(TAG_SERIAL, serial == null ? "" : serial);

		ListTag filesTag = new ListTag();
		int total = 0;
		for (List<String> file : files) {
			if (total >= MAX_RECORDS) break;
			ListTag fileTag = new ListTag();
			int inFile = 0;
			for (String rec : file) {
				if (inFile >= MAX_RECORDS_PER_FILE || total >= MAX_RECORDS) break;
				fileTag.add(StringTag.valueOf(rec == null ? "" : rec));
				inFile++;
				total++;
			}
			filesTag.add(fileTag);
		}
		tag.put(TAG_FILES, filesTag);
		return s;
	}

	// --- Access ---------------------------------------------------------

	public static String getVolumeSerial(ItemStack s) {
		CompoundTag tag = s.getTag();
		return tag == null ? "" : tag.getString(TAG_SERIAL);
	}

	public static void setVolumeSerial(ItemStack s, String serial) {
		s.getOrCreateTag().putString(TAG_SERIAL, serial == null ? "" : serial);
	}

	public static List<List<String>> getFiles(ItemStack s) {
		List<List<String>> out = new ArrayList<>();
		CompoundTag tag = s.getTag();
		if (tag == null) return out;

		ListTag filesTag = tag.getList(TAG_FILES, Tag.TAG_LIST);
		for (int i = 0; i < filesTag.size(); i++) {
			ListTag fileTag = filesTag.getList(i);
			List<String> file = new ArrayList<>(fileTag.size());
			for (int j = 0; j < fileTag.size(); j++) {
				file.add(fileTag.getString(j));
			}
			out.add(file);
		}
		return out;
	}

	public static boolean isBlank(ItemStack s) {
		return getVolumeSerial(s).isEmpty() && getFiles(s).isEmpty();
	}
}
