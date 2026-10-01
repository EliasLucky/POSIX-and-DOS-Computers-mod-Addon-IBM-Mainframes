package com.eliaslucky.ibm_mainframe.items;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A punched card. Holds exactly one 80-character string in NBT.
 * Non-stackable, because each card is unique content.
 */
public class PunchCardItem extends Item {
	private static final String TAG = "CardText";
	public static final int COLUMNS = 80;

	public PunchCardItem(Properties p) {
		super(p.stacksTo(1));
	}

	public static ItemStack of(String content) {
		ItemStack stack = new ItemStack(ModItems.PUNCH_CARD.get());
		String clamped = content == null ? "" : content;
		if (clamped.length() > COLUMNS) clamped = clamped.substring(0, COLUMNS);
		// Right-pad to 80 columns so every card has identical width.
		StringBuilder padded = new StringBuilder(clamped);
		while (padded.length() < COLUMNS) padded.append(' ');
		CompoundTag tag = stack.getOrCreateTag();
		tag.putString(TAG, padded.toString());
		return stack;
	}

	public static String getContent(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return (tag == null || !tag.contains(TAG)) ? "" : tag.getString(TAG);
	}
}
