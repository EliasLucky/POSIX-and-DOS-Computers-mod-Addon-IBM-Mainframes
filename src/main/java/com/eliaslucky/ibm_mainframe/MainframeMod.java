package com.eliaslucky.ibm_mainframe;

import com.eliaslucky.mc_dos.blocks.ICustomCreativeTab;
import com.eliaslucky.ibm_mainframe.network.ModMessages;
import com.mojang.logging.LogUtils;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MainframeMod.MODID)
public class MainframeMod
{
	public static final String MODID = "ibm_mainframes";
	public static final String NAME = "IBM Mainframes addon for \"POSIX and DOS Computers mod\"";

	public static final Logger LOGGER = LogUtils.getLogger();

	public MainframeMod(FMLJavaModLoadingContext context)
	{
		IEventBus modEventBus = context.getModEventBus();
        
		AllCreativeModeTabs.register(modEventBus);
		AllBlocks.BLOCKS.register(modEventBus);
		AllBlocks.BLOCK_ITEMS.register(modEventBus);
		AllBlockEntities.BLOCK_ENTITIES.register(modEventBus);
		AllItems.ITEMS.register(modEventBus);
       
		modEventBus.addListener(this::commonSetup);
		modEventBus.addListener(this::buildContents);
        
		MinecraftForge.EVENT_BUS.register(this);     
	}

	public static void init(final FMLCommonSetupEvent event) {
	
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		event.enqueueWork(ModMessages::register);
		event.enqueueWork(() -> {
		    // Machine types
			
			for (BuiltInMainframes type : BuiltInMainframes.values()) {
				MachineTypeRegistry.register(type);
			}

			// Programs available to JCL
			PorgramRegistry.register("FORT", new FortProgram());
		});
	}

	public void buildContents(BuildCreativeModeTabContentsEvent event) {
		AllBlocks.BLOCKS.getEntries().forEach(registryObject -> {
			Block block = registryObject.get();

			if (block instanceof ICustomCreativeTab customTabBlock) {
				if (event.getTabKey() == customTabBlock.getCreativeTab()) {
					Item blockItem = block.asItem();

					if (blockItem != Items.AIR) {
						event.accept(blockItem);	
					}
				}
			}
		});
		AllItems.ITEMS.getEntries().forEach(registryObject -> {
			Item item = registryObject.get();
			if (item instanceof ICustomCreativeTab customTabItem) {
				if (event.getTabKey() == customTabItem.getCreativeTab()) {
					event.accept(item);
				}
			}
		});
	}
}
