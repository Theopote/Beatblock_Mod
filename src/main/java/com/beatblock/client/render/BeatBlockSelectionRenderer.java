package com.beatblock.client.render;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.input.BeatBlockInputSystem;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/**
 * 在世界中绘制当前选区的总包围盒线框（优化：不按方块逐个描边，大选区仍保持低开销）。
 * 框选 / 线选：第一点后沿光标预览第二角包围盒；球选：预览球体包络盒。
 */
public final class BeatBlockSelectionRenderer {

	private static final int COLOR_ARGB = 0xE6FFB833;
	private static final int BOX_PREVIEW_ARGB = 0xAABBDDFF;
	private static final int LINE_PREVIEW_ARGB = 0xAAFFCC66;
	private static final int SPHERE_PREVIEW_ARGB = 0xAADD88FF;

	private BeatBlockSelectionRenderer() {}

	public static void renderIfNeeded(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.gameRenderer == null) return;
		if (!(mc.currentScreen instanceof BeatBlockUIScreen)) return;

		var mgr = BeatBlockSelectionManager.get();

		if (mgr.getSelectionCount() > 0) {
			BlockPos min = mgr.getBoundingMin();
			BlockPos max = mgr.getBoundingMax();
			if (min != null && max != null) {
				drawInclusiveBoundingBox(matrices, consumers, mc, min, max, COLOR_ARGB, 2.5f);
			}
		}

		renderBoxDragPreviewIfNeeded(matrices, consumers, mc, mgr);
		renderLineDragPreviewIfNeeded(matrices, consumers, mc, mgr);
		renderSphereBrushPreviewIfNeeded(matrices, consumers, mc, mgr);
	}

	private static void renderBoxDragPreviewIfNeeded(
			MatrixStack matrices, VertexConsumerProvider consumers,
			MinecraftClient mc, BeatBlockSelectionManager mgr) {
		if (mgr.getMode() != SelectionMode.BOX || mgr.getBoxFirstCorner() == null) return;
		BlockHitResult hit = raycastForPreview(mc);
		if (hit == null) return;
		drawAabbBetweenCorners(matrices, consumers, mc, mgr.getBoxFirstCorner(), hit.getBlockPos(), BOX_PREVIEW_ARGB, 2.0f);
	}

	private static void renderLineDragPreviewIfNeeded(
			MatrixStack matrices, VertexConsumerProvider consumers,
			MinecraftClient mc, BeatBlockSelectionManager mgr) {
		if (mgr.getMode() != SelectionMode.LINE || mgr.getLineFirstCorner() == null) return;
		BlockHitResult hit = raycastForPreview(mc);
		if (hit == null) return;
		drawAabbBetweenCorners(matrices, consumers, mc, mgr.getLineFirstCorner(), hit.getBlockPos(), LINE_PREVIEW_ARGB, 2.0f);
	}

	private static void renderSphereBrushPreviewIfNeeded(
			MatrixStack matrices, VertexConsumerProvider consumers,
			MinecraftClient mc, BeatBlockSelectionManager mgr) {
		if (mgr.getMode() != SelectionMode.SPHERE) return;
		BlockHitResult hit = raycastForPreview(mc);
		if (hit == null) return;
		BlockPos c = hit.getBlockPos();
		int r = mgr.getSphereBrushRadius();
		int minX = c.getX() - r;
		int minY = c.getY() - r;
		int minZ = c.getZ() - r;
		int maxX = c.getX() + r;
		int maxY = c.getY() + r;
		int maxZ = c.getZ() + r;
		drawInclusiveBoundingBox(matrices, consumers, mc,
				new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
				SPHERE_PREVIEW_ARGB, 1.75f);
	}

	private static BlockHitResult raycastForPreview(MinecraftClient mc) {
		if (BeatBlockUIScreen.isMouseOverUI()) return null;
		BlockHitResult hit = BeatBlockInputSystem.raycastFromImGui();
		if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		double maxSq = BeatBlockInputSystem.MAX_RAYCAST_DISTANCE * BeatBlockInputSystem.MAX_RAYCAST_DISTANCE;
		if (cam.squaredDistanceTo(hit.getPos()) > maxSq) return null;
		return hit;
	}

	private static void drawAabbBetweenCorners(
			MatrixStack matrices, VertexConsumerProvider consumers, MinecraftClient mc,
			BlockPos first, BlockPos second, int argb, float lineWidth) {
		int minX = Math.min(first.getX(), second.getX());
		int minY = Math.min(first.getY(), second.getY());
		int minZ = Math.min(first.getZ(), second.getZ());
		int maxX = Math.max(first.getX(), second.getX());
		int maxY = Math.max(first.getY(), second.getY());
		int maxZ = Math.max(first.getZ(), second.getZ());
		drawInclusiveBoundingBox(matrices, consumers, mc,
				new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), argb, lineWidth);
	}

	private static void drawInclusiveBoundingBox(
			MatrixStack matrices, VertexConsumerProvider consumers, MinecraftClient mc,
			BlockPos minInclusive, BlockPos maxInclusive, int argb, float lineWidth) {
		var cam = mc.gameRenderer.getCamera().getCameraPos();
		double ox = minInclusive.getX() - cam.x;
		double oy = minInclusive.getY() - cam.y;
		double oz = minInclusive.getZ() - cam.z;
		double dx = maxInclusive.getX() - minInclusive.getX() + 1.0;
		double dy = maxInclusive.getY() - minInclusive.getY() + 1.0;
		double dz = maxInclusive.getZ() - minInclusive.getZ() + 1.0;

		var shape = VoxelShapes.cuboid(0, 0, 0, dx, dy, dz);
		VertexConsumer buffer = consumers.getBuffer(RenderLayers.LINES);
		matrices.push();
		VertexRendering.drawOutline(matrices, buffer, shape, ox, oy, oz, argb, lineWidth);
		matrices.pop();
	}
}
