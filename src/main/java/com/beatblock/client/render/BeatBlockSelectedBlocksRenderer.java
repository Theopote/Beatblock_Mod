package com.beatblock.client.render;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.selection.BeatBlockSelectionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * 为选中方块绘制描边与可选半透明填充；在 {@link #MAX_BLOCKS_FOR_PER_BLOCK_RENDER} 以内时用
 * {@link SelectionVoxelGreedyMesher} 对暴露面做贪婪合并，否则仅依赖总包围盒（由 {@link BeatBlockSelectionRenderer}）。
 */
public final class BeatBlockSelectedBlocksRenderer {

	public static final int MAX_BLOCKS_FOR_PER_BLOCK_RENDER = 6000;
	private static final double RENDER_DISTANCE_SQ = 192.0 * 192.0;
	private static final int OUTLINE_ARGB = 0xE6FFCC44;
	private static final float OUTLINE_WIDTH = 1.35f;

	private BeatBlockSelectedBlocksRenderer() {}

	public static void renderIfNeeded(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.gameRenderer == null) return;
		if (!(mc.currentScreen instanceof BeatBlockUIScreen)) return;

		var mgr = BeatBlockSelectionManager.get();
		int n = mgr.getSelectionCount();
		if (n == 0 || n > MAX_BLOCKS_FOR_PER_BLOCK_RENDER) return;

		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		boolean wantFill = mgr.isSelectionFillEnabled();
		var fillBuf = wantFill ? consumers.getBuffer(RenderLayers.debugFilledBox()) : null;
		var lineBuf = consumers.getBuffer(RenderLayers.LINES);

		SelectionVoxelGreedyMesher.render(
				mgr.getSelectedBlocks(),
				cam,
				matrices,
				lineBuf,
				fillBuf,
				wantFill,
				RENDER_DISTANCE_SQ,
				OUTLINE_ARGB,
				OUTLINE_WIDTH,
				1.0f,
				0.78f,
				0.15f,
				0.14f);
	}
}
