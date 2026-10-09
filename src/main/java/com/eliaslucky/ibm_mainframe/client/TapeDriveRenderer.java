package com.eliaslucky.ibm_mainframe.client;

import com.eliaslucky.mc_dos.blocks.DirectionalHorizontalBlock;
import com.eliaslucky.ibm_mainframe.blocks.TapeDriveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the two reels of the IBM 2401 and rotates them according to
 * the BE's state machine.
 *
 * <p>The cabinet, front panel are the block's
 * JSON model, which renders normally. Only the reels come from the
 * two ModelParts registered via {@link ModModelLayers}.
 *
 * <p>Between server syncs, the renderer extrapolates each reel's
 * angle using the last-synced angle and speed. The extrapolation is
 * what makes the rotation look smooth at 60 fps even though the
 * server sends updates at 4 Hz.
 */
public class TapeDriveRenderer implements BlockEntityRenderer<TapeDriveBlockEntity> {
	private static final ResourceLocation REEL_TEX = ResourceLocation.fromNamespaceAndPath("ibm_mainframe", "textures/block/tape_reel.png");

	/** Same constant as the BE. Kept in sync so extrapolation matches. */
	private static final float BASE_ANGULAR = 90.0f;
	private static final float HUB_RATIO	= 0.35f;

	private final ModelPart leftReel;
	private final ModelPart rightReel;

	public TapeDriveRenderer(BlockEntityRendererProvider.Context ctx) {
		this.leftReel  = ctx.bakeLayer(ModModelLayers.IBM_2401_TAPE_REEL_LEFT);
		this.rightReel = ctx.bakeLayer(ModModelLayers.IBM_2401_TAPE_REEL_RIGHT);
	}

	@Override
	public void render(TapeDriveBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
		BlockState state = be.getBlockState();
		Direction facing = state.getValue(DirectionalHorizontalBlock.FACING);

		// Current speed at render time, including client-side ramp.
		// We use the server-synced speed as an approximation; the
		// ramp between states is short and the interpolation gap
		// is small enough that this reads correctly.
		float speed = be.getCurrentSpeed();

		// --- Extrapolated angles ---
		long now = System.currentTimeMillis();
		long sinceSync = now - be.getLastClientSyncMillis();
		if (sinceSync < 0) sinceSync = 0;
		if (sinceSync > 500) sinceSync = 500;	// cap for long pauses
		float dt = sinceSync / 1000.0f;

		float rL = reelRadius(1.0f - be.getTapeProgress());
		float rR = reelRadius(be.getTapeProgress());
		float omegaL = BASE_ANGULAR * speed / rL;
		float omegaR = BASE_ANGULAR * speed / rR;

		float leftAngle  = be.getLeftAngle()  + omegaL * dt;
		float rightAngle = be.getRightAngle() - omegaR * dt;

		// --- Pose: move to block center, face the right way ---
		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
		pose.translate(0, -0.5, 0);		  // model sits on the block floor

		VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(REEL_TEX));

		// Left reel: only visible with a tape inserted.
		if (be.hasTape()) {
			leftReel.zRot = (float) Math.toRadians(leftAngle);
			leftReel.render(pose, vc, light, overlay);
		}

		// Right reel: always present.
		rightReel.zRot = (float) Math.toRadians(rightAngle);
		rightReel.render(pose, vc, light, overlay);

		pose.popPose();
	}

	/** Same formula as the BE's radius function. */
	private static float reelRadius(float p) {
		float h = HUB_RATIO;
		return (float) Math.sqrt(h * h + p * (1.0f - h * h));
	}
}
