package com.eliaslucky.ibm_mainframe.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/** Model layer identifiers for the addon. */
public final class ModModelLayers {
	private ModModelLayers() {}

	public static final ModelLayerLocation TAPE_REEL_LEFT = new ModelLayerLocation(
			ResourceLocation.fromNamespaceAndPath(MainframeMod.MODID, "tape_reel"),
			"left");

	public static final ModelLayerLocation TAPE_REEL_RIGHT = new ModelLayerLocation(
			ResourceLocation.fromNamespaceAndPath(MainframeMod.MODID, "tape_reel"),
			"right");
}
