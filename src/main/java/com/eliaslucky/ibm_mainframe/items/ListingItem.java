package com.eliaslucky.ibm_mainframe.items;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import com.eliaslucky.ibm_mainframe.AllItems;

import java.util.List;

/** A printed listing from the 1403. Holds text in NBT. Non-stackable. */
public class ListingItem extends Item {
	private static final String TAG = "ListingText";
	public static final int MAX_LENGTH = 64 * 1024;

	public ListingItem(Properties p) {
		super(p.stacksTo(1));
	}

	public static ItemStack of(String text) {
		ItemStack stack = new ItemStack(AllItems.PRINTER_LISTING_PAPER.get());
		String t = text == null ? "" : text;
		if (t.length() > MAX_LENGTH) t = t.substring(0, MAX_LENGTH);
		CompoundTag tag = stack.getOrCreateTag();
		tag.putString(TAG, t);
		return stack;
	}

	public static String getText(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return (tag == null || !tag.contains(TAG)) ? "" : tag.getString(TAG);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		String t = getText(stack);
		int lines = t.isEmpty() ? 0 : t.split("\n", -1).length;
		tooltip.add(Component.literal(lines + " lines"));
	}
}
