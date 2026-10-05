package com.eliaslucky.ibm_mainframe.client;

import com.eliaslucky.ibm_mainframe.MainframeMod;
import com.eliaslucky.ibm_mainframe.blocks.ModBlockEntities;
import com.eliaslucky.ibm_mainframe.client.model.LeftReelModel;
import com.eliaslucky.ibm_mainframe.client.model.RightReelModel;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side setup. Registers model layers, block entity renderers,
 * and any other client-only resources the addon needs.
 *
 * <p>This class is discovered by Forge via the
 * {@code @Mod.EventBusSubscriber} annotation. The bus is
 * {@link Mod.EventBusSubscriber.Bus#MOD}, so all three events below
 * fire on the mod loading bus during startup.
 */
@Mod.EventBusSubscriber(modid = MainframeMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
	private ClientSetup() {}

	/**
	 * Fires once during client mod loading, before any world is
	 * joined. Use this for anything that doesn't need a world or
	 * a resource manager.
	 */
	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {

		});
	}

	/**
	 * Fires once, after the model system is initialized. Register
	 * every LayerDefinition the addon needs here. If a layer is
	 * not registered, any renderer that calls
	 * {@code ctx.bakeLayer(...)} for it will crash.
	 */
	@SubscribeEvent
	public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(ModModelLayers.TAPE_REEL_LEFT,
				LeftReelModel::createBodyLayer);
		event.registerLayerDefinition(ModModelLayers.TAPE_REEL_RIGHT,
				RightReelModel::createBodyLayer);
	}

	/**
	 * Fires once, after layer definitions are available. Register
	 * every block entity renderer here.
	 */
	@SubscribeEvent
	public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(
				ModBlockEntities.TAPE_DRIVE.get(),
				TapeDriveRenderer::new);
	}
}
