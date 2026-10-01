package com.eliaslucky.ibm_mainframe.items;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered stack of punched cards. Each entry is one 80-column
 * string. Non-stackable, identity lives in NBT.
 *
 * <p>Breaking a deck gives back individual {@link PunchCardItem}s
 */
public class CardDeckItem extends Item {
	private static final String TAG = "Cards";
	public static final int MAX_CARDS = 2000;

	public CardDeckItem(Properties p) {
		super(p.stacksTo(1));
	}

	public static ItemStack of(List<String> cards) {
		ItemStack stack = new ItemStack(ModItems.CARD_DECK.get());
		ListTag list = new ListTag();
		int n = Math.min(cards.size(), MAX_CARDS);
		for (int i = 0; i < n; i++) {
			String c = cards.get(i) == null ? "" : cards.get(i);
			if (c.length() > PunchCardItem.COLUMNS) c = c.substring(0, PunchCardItem.COLUMNS);
			StringBuilder padded = new StringBuilder(c);
			while (padded.length() < PunchCardItem.COLUMNS) padded.append(' ');
			list.add(StringTag.valueOf(padded.toString()));
		}
		CompoundTag tag = stack.getOrCreateTag();
		tag.put(TAG, list);
		return stack;
	}

	public static List<String> getCards(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(TAG)) return List.of();
		ListTag list = tag.getList(TAG, Tag.TAG_STRING);
		List<String> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
		return out;
	}

	public static boolean isEmpty(ItemStack stack) {
		return getCards(stack).isEmpty();
	}
}
