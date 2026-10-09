package com.eliaslucky.ibm_mainframe;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.eliaslucky.ibm_mainframe.blocks.Console1052Block;
import com.eliaslucky.ibm_mainframe.blocks.Console3270Block;
import com.eliaslucky.ibm_mainframe.blocks.MainframeBlock;
import com.eliaslucky.ibm_mainframe.blocks.CardReaderBlock;
import com.eliaslucky.ibm_mainframe.blocks.LinePrinterBlock;
import com.eliaslucky.ibm_mainframe.blocks.TapeDriveBlock;
import com.eliaslucky.ibm_mainframe.channel.ChannelCableBlock;
import com.eliaslucky.ibm_mainframe.machine.BuiltInMainframes;
import com.eliaslucky.ibm_mainframe.blocks.DiskDriveBlock;
import com.eliaslucky.ibm_mainframe.blocks.KeypunchBlock;

import java.util.function.Supplier;

public class AllBlocks {
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MainframeMod.MODID);
	public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MainframeMod.MODID);

	public static final RegistryObject<Block> CHANNEL_BLOCK = registerBlock("channel_cable_block",
			() -> new ChannelCableBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(2.0F,2.0F)
				.sound(SoundType.STONE)
				.requiresCorrectToolForDrops()
				.ignitedByLava()
			), new Item.Properties()
		);

	// MAINFRAMES
	public static final RegistryObject<Block> IBM_MAINFRAME_S360 = registerBlock("ibm_mainframe_s360",
			() -> new MainframeBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(3.5F)
				.sound(SoundType.METAL)
				.noOcclusion()
				.requiresCorrectToolForDrops(), BuiltInMainframes.S360_MODEL_30
			), new Item.Properties()
		);

	// PERIPHERALS
	public static final RegistryObject<Block> IBM_2540_CARD_READER = registerBlock("ibm_2540_card_reader",
			() -> new CardReaderBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(2.0F,2.0F)
				.sound(SoundType.METAL)
				.noOcclusion()
				.requiresCorrectToolForDrops()
			), new Item.Properties()
		);
	
	public static final RegistryObject<Block> IBM_029_LINE_PRINTER = registerBlock("ibm_029_line_printer",
			() -> new LinePrinterBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(2.0F,2.0F)
				.sound(SoundType.METAL)
				.noOcclusion()
				.requiresCorrectToolForDrops()
			), new Item.Properties()
		);

	public static final RegistryObject<Block> IBM_TAPE_DRIVE = registerBlock("ibm_tape_drive",
			() -> new TapeDriveBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.STONE)
				.strength(2.0F,2.0F)
				.sound(SoundType.STONE)
				.noOcclusion()
			), new Item.Properties()
		);

	public static final RegistryObject<Block> IBM_2311_DISK_DRIVE = registerBlock("ibm_2311_disk_drive",
			() -> new DiskDriveBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(2.0F,2.0F)
				.sound(SoundType.METAL)
				.noOcclusion()
				.requiresCorrectToolForDrops()
			), new Item.Properties()
		);

	public static final RegistryObject<Block> IBM_1052_CONSOLE = registerBlock("ibm_1052_console",
			() -> new Console1052Block(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(2.0F)
				.sound(SoundType.METAL)
				.noOcclusion()
				.requiresCorrectToolForDrops()
			), new Item.Properties()
		);

	public static final RegistryObject<Block> IBM_3270_CONSOLE = registerBlock("ibm_3270_console",
			() -> new Console3270Block(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(2.0F)
				.sound(SoundType.METAL)
				.noOcclusion()
				.requiresCorrectToolForDrops()
			), new Item.Properties()
		);

	// PROGRAMMING TOOLS
	public static final RegistryObject<Block> IBM_KEYPUNCH = registerBlock("ibm_keypunch",
			() -> new KeypunchBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(2.0F,2.0f)
				.sound(SoundType.METAL)
				.noOcclusion()
			), new Item.Properties()
		);

	private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block, Item.Properties props) {
		RegistryObject<T> registered = BLOCKS.register(name,block);
		BLOCK_ITEMS.register(name, () -> new BlockItem(registered.get(), props));
		return registered;
	}
}
