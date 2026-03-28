package com.beatblock.client.render;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.selection.BeatBlockSelectionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.client.render.RenderLayers;

/**
 * 在世界中绘制当前选区的总包围盒线框（优化：不按方块逐个描边，大选区仍保持低开销）。
 */
public final class BeatBlockSelectionRenderer {

	private static final int COLOR_ARGB = 0xE6FFB833;

	private BeatBlockSelectionRenderer() {}

	public static void renderIfNeeded(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.gameRenderer == null) return;
		if (!(mc.currentScreen instanceof BeatBlockUIScreen)) return;

		var mgr = BeatBlockSelectionManager.get();
		if (mgr.getSelectionCount() == 0) return;

		BlockPos min = mgr.getBoundingMin();
		BlockPos max = mgr.getBoundingMax();
		if (min == null || max == null) return;

		var cam = mc.gameRenderer.getCamera().getCameraPos();
		double ox = min.getX() - cam.x;
		double oy = min.getY() - cam.y;
		double oz = min.getZ() - cam.z;
		double dx = max.getX() - min.getX() + 1.0;
		double dy = max.getY() - min.getY() + 1.0;
		double dz = max.getZ() - min.getZ() + 1.0;

		var shape = VoxelShapes.cuboid(0, 0, 0, dx, dy, dz);
		VertexConsumer buffer = consumers.getBuffer(RenderLayers.LINES);
		matrices.push();
		VertexRendering.drawOutline(matrices, buffer, shape, ox, oy, oz, COLOR_ARGB, 2.5f);
		matrices.pop();
	}
}
