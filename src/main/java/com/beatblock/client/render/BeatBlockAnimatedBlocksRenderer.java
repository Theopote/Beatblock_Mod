package com.beatblock.client.render;

import com.beatblock.BeatBlock;
import com.beatblock.engine.AnimatedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link com.beatblock.engine.BlockAnimationEngine#getCurrentFrameBlocks()} 画成真实方块模型
 * （与 BlockDisplay 同款渲染路径：{@link net.minecraft.client.render.block.BlockRenderManager#renderBlockAsEntity}）。
 */
public final class BeatBlockAnimatedBlocksRenderer {

	/** 实体方块绘制比线框贵，默认上限略低；仍覆盖常见舞台规模。 */
	private static final int MAX_DRAW_BLOCKS = 1024;

	private BeatBlockAnimatedBlocksRenderer() {}

	public static void render(MatrixStack matrices, VertexConsumerProvider consumers) {
		if (BeatBlock.blockAnimationEngine == null) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc.world;
		if (world == null || mc.gameRenderer == null || mc.getBlockRenderManager() == null) {
			return;
		}
		Map<BlockPos, AnimatedBlock> frame = BeatBlock.blockAnimationEngine.getCurrentFrameBlocks();
		if (frame == null || frame.isEmpty()) {
			return;
		}

		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		List<Map.Entry<BlockPos, AnimatedBlock>> entries = new ArrayList<>(frame.entrySet());
		entries.sort(Comparator.comparing(e -> e.getKey().asLong()));

		int n = Math.min(entries.size(), MAX_DRAW_BLOCKS);
		var drm = mc.getBlockRenderManager();

		for (int i = 0; i < n; i++) {
			Map.Entry<BlockPos, AnimatedBlock> e = entries.get(i);
			BlockPos orig = e.getKey();
			AnimatedBlock ab = e.getValue();
			if (!world.isChunkLoaded(orig)) {
				continue;
			}
			BlockState state = world.getBlockState(orig);
			if (state.isAir()) {
				continue;
			}

			Vec3d anim = ab.getPosition();
			double minX = anim.x - 0.5;
			double minY = anim.y;
			double minZ = anim.z - 0.5;
			BlockPos lightPos = BlockPos.ofFloored(anim.x, anim.y + 0.5, anim.z);
			int light = WorldRenderer.getLightmapCoordinates(world, lightPos);

			matrices.push();
			matrices.translate(minX - cam.x, minY - cam.y, minZ - cam.z);
			matrices.translate(0.5, 0.5, 0.5);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ab.getRotationYaw()));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ab.getRotationPitch()));
			float s = ab.getScale();
			matrices.scale(s, s, s);
			matrices.translate(-0.5, -0.5, -0.5);

			drm.renderBlockAsEntity(state, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}
	}
}
