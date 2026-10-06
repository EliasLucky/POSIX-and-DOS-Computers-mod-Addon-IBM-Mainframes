package com.eliaslucky.ibm_mainframe.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class RightReelTapeDriveModel<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "ibm_tape_drive_reels252"), "main");
	private final ModelPart group;
	private final ModelPart reel2;

	public RightReelTapeDriveModel(ModelPart root) {
		this.group = root.getChild("group");
		this.reel2 = this.group.getChild("reel2");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition group = partdefinition.addOrReplaceChild("group", CubeListBuilder.create(), PartPose.offset(14.0F, 24.0F, 0.0F));

		PartDefinition reel2 = group.addOrReplaceChild("reel2", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0233F, -0.6312F, -0.74F, 6.0F, 1.23F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.7233F, -0.4012F, -1.64F, 1.5F, 0.6F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.2733F, -2.1512F, -0.84F, 0.6F, 1.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.2733F, -0.8512F, -1.64F, 0.6F, 1.5F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.6383F, -3.0162F, -0.74F, 1.23F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-10.4767F, -24.8988F, -4.86F));

		PartDefinition cube_r9 = reel2.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(0, 0).addBox(-0.615F, -3.0F, -1.0F, 1.23F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.0F, -0.615F, -1.0F, 6.0F, 1.23F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0233F, -0.0162F, 0.26F, 0.0F, 0.0F, 0.3927F));

		PartDefinition cube_r10 = reel2.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -0.615F, -1.0F, 6.0F, 1.23F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.615F, -3.0F, -1.0F, 1.23F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0233F, -0.0162F, 0.26F, 0.0F, 0.0F, -0.3927F));

		PartDefinition cube_r11 = reel2.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -0.615F, -1.0F, 6.0F, 1.23F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0233F, -0.0162F, 0.26F, 0.0F, 0.0F, -0.7854F));

		PartDefinition cube_r12 = reel2.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -0.615F, -1.0F, 6.0F, 1.23F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0233F, -0.0162F, 0.26F, 0.0F, 0.0F, 0.7854F));

		PartDefinition cube_r13 = reel2.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(0, 0).addBox(-0.3F, -0.75F, -1.0F, 0.6F, 1.5F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0267F, -0.1012F, -0.64F, 0.0F, 0.0F, 0.7854F));

		PartDefinition cube_r14 = reel2.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(0, 0).addBox(-0.3F, -0.75F, -1.0F, 0.6F, 1.5F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0267F, -0.1012F, -0.64F, 0.0F, 0.0F, -0.7854F));

		PartDefinition cube_r15 = reel2.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(0, 0).addBox(-0.3F, -0.65F, -1.0F, 0.6F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.1733F, 1.1988F, 0.16F, 0.0F, 0.0F, 0.7854F));

		PartDefinition cube_r16 = reel2.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(0, 0).addBox(-0.65F, -0.3F, -1.0F, 1.0F, 0.6F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.2267F, 1.1988F, 0.16F, 0.0F, 0.0F, 0.7854F));

		return LayerDefinition.create(meshdefinition, 16, 16);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		group.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}