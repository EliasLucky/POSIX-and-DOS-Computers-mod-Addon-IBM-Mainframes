package com.eliaslucky.ibm_mainframe;

import com.eliaslucky.ibm_mainframe.items.*;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AllItems {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Computers.MODID);

	public static final RegistryObject<Item> BLANK_CARD = ITEMS.register("blank_card",
			() -> new BlankCardItem(new Item.Properties())
		);

	public static final RegistryObject<Item> PUNCH_CARD = ITEMS.register("punch_card",
			() -> new PunchCardItem(new Item.Properties())
		);
		
	public static final RegistryObject<Item> CARD_DECK = ITEMS.register("card_deck",
			() -> new CardDeckItem(new Item.Properties())
		);

	public static final RegistryObject<Item> PRINTER_LISTING_PAPER = ITEMS.register("printer_listing_paper",
			() -> new ListingItem(new Item.Properties())
		);

	public static final RegisterObject<Item> MAGNETIC_TAPE = ITEMS.register("magnetic_tape",
			() -> new MagneticTapeItem(new Item.Properties())
		);

	public static final RegistryObject<Item> DISK_PACK = ITEMS.register("disk_pack",
			() -> new DiskPackItem(new Item.Properties())
		);
}
