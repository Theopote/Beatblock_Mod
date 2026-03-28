package com.beatblock.client.render;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.selection.BeatBlockSelectionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 为选中的每个方块绘制描边（大选区仅依赖 {@link BeatBlockSelectionRenderer} 的总包围盒）。
 * 可选半透明填充（每块 6 面，开销较高）。
 */
public final class BeatBlockSelectedBlocksRenderer {

	/** 超过此数量时不再逐块绘制，避免帧时间爆炸。 */
	public static final int MAX_BLOCKS_FOR_PER_BLOCK_RENDER = 6000;
	private static final double RENDER_DISTANCE_SQ = 192.0 * 192.0;
	private static final int OUTLINE_ARGB = 0xE6FFCC44;
	private static final float OUTLINE_WIDTH = 1.35f;
	/** 填充面颜色：金黄半透明 */
	private static final float FILL_R = 1.0f;
	private static final float FILL_G = 0.78f;
	private static final float FILL_B = 0.15f;
	private static final float FILL_A = 0.12f;

	private BeatBlockSelectedBlocksRenderer() {}

	public static void renderIfNeeded(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.gameRenderer == null) return;
		if (!(mc.currentScreen instanceof BeatBlockUIScreen)) return;

		var mgr = BeatBlockSelectionManager.get();
		int n = mgr.getSelectionCount();
		if (n == 0 || n > MAX_BLOCKS_FOR_PER_BLOCK_RENDER) return;

		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		VertexConsumer lineBuffer = consumers.getBuffer(RenderLayers.LINES);
		boolean fill = mgr.isSelectionFillEnabled();

		for (BlockPos p : mgr.getSelectedBlocks()) {
			if (cam.squaredDistanceTo(Vec3d.ofCenter(p)) > RENDER_DISTANCE_SQ) continue;
			var state = mc.world.getBlockState(p);
			var shape = state.getOutlineShape(mc.world, p);
			if (shape == null || shape.isEmpty()) continue;

			double ox = p.getX() - cam.x;
			double oy = p.getY() - cam.y;
			double oz = p.getZ() - cam.z;

			matrices.push();
			VertexRendering.drawOutline(matrices, lineBuffer, shape, ox, oy, oz, OUTLINE_ARGB, OUTLINE_WIDTH);
			matrices.pop();

			if (fill) {
				drawBlockFill(matrices, consumers, ox, oy, oz);
			}
		}
	}

	private static void drawBlockFill(MatrixStack matrices, VertexConsumerProvider consumers, double ox, double oy, double oz) {
		VertexConsumer buf = consumers.getBuffer(RenderLayers.debugFilledBox());
		var m = matrices.peek().getPositionMatrix();
		float x0 = (float) ox;
		float y0 = (float) oy;
		float z0 = (float) oz;
		float x1 = x0 + 1f;
		float y1 = y0 + 1f;
		float z1 = z0 + 1f;

		// bottom (y0)
		quad(buf, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
		// top
		quad(buf, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0);
		// north (z0)
		quad(buf, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1);
		// south
		quad(buf, m, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1);
		// west (x0)
		quad(buf, m, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0);
		// east
		quad(buf, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0);
	}

	private static void quad(VertexConsumer buf, org.joml.Matrix4f m,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float nx, float ny, float nz) {
		buf.vertex(m, x0, y0, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).normal(nx, ny, nz);
		buf.vertex(m, x1, y1, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).normal(nx, ny, nz);
		buf.vertex(m, x2, y2, z2).color(FILL_R, FILL_G, FILL_B, FILL_A).normal(nx, ny, nz);
		buf.vertex(m, x3, y3, z3).color(FILL_R, FILL_G, FILL_B, FILL_A).normal(nx, ny, nz);
	}
}
