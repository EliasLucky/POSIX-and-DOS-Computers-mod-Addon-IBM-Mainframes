package com.eliaslucky.ibm_mainframe;

import com.eliaslucky.ibm_mainframe.blocks.CardReaderBlockEntity;
import com.eliaslucky.ibm_mainframe.blocks.LinePrinterBlockEntity;
import com.eliaslucky.ibm_mainframe.blocks.TapeDriveBlockEntity;
import com.eliaslucky.ibm_mainframe.blocks.DiskDriveBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AllBlockEntities {
	public static final DeferredReegister<BlockEntityEntity<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPE, MainframeMod.MODID);
	
	public static final RegistryObject<BlockEntityType<CardReaderBlockEntity>> IBM_2540_CARD_READER = BLOCK_ENTITIES.register("ibm_2540_card_reader",
        	() -> BlockEntityType.Builder.of(CardReaderBlockEntity::new,
			AllBlocks.IBM_CARD_READER.get()).build(null)
	);

	public static final RegistryObject<BlockEntityType<LinePrinterBlockEntity>> IBM_029_LINE_PRINTER = BLOCK_ENTITIES.register("ibm_029_line_printer",
		() -> BlockEntityType.Builder.of(LinePrinterBlockEntity::new,
			AllBlocks.IBM_029_LINE_PRINTER.get()).build(null)
	);

	public static final RegistryObject<BlockEntityType<TapeDriveBlockEntity>> TAPE_DRIVE = BLOCK_ENTITIES.register("ibm_tape_drive",
		() -> BlockEntityType.Builder.of(TapeDriveBlockEntity::new,
			AllBlocks.IBM_TAPE_DRIVE.get()).build(null)
	);

	public static final RegistryObject<BlockEntityType<DiskDriveBlockEntity>> IBM_2311_DISK_DRIVE = BLOCK_ENTITIES.register("ibm_2311_disk_drive",
		() -> BlockEntityType.Builder.of(DiskDriveBlockEntity::new,
			AllBlocks.IBM_2311_DISK_DRIVE.get()).build(null)
	);
	public static final RegistryObject<BlockEntityType<ConsoleBlockEntity>> IBM_1035_CONSOLE = BLOCK_ENTITIES.register("ibm_1035_console",
		() -> BlockEntityType.Builder.of(ConsoleBlockEntity::new,
			AllBlocks.IBM_1035_CONSOLE.get()).build(null)
	);
}
