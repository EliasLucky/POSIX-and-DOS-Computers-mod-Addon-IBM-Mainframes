package com.eliaslucky.ibm_mainframe.client;

import com.eliaslucky.ibm_mainframe.client.model.TapeDriveModel;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public final class ModModelLayers {
    private ModModelLayers() {}

    public static final ModelLayerLocation TAPE_DRIVE = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(MainframeMod.MODID, "tape_drive"),
            "main");
}
