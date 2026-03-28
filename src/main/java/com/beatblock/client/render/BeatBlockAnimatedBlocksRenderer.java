package com.beatblock.client.render;

import com.beatblock.BeatBlock;
import com.beatblock.engine.AnimatedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link com.beatblock.engine.BlockAnimationEngine#getCurrentFrameBlocks()} 画成真实方块模型。
 * 使用 {@link BlockRenderManager#renderBlock} 并在<strong>原始格位</strong>采样生物群系/环境光，
 * 避免 {@link BlockRenderManager#renderBlockAsEntity} 无 BlockPos 时色调与生态不一致。
 * 与真实方块完全重合且未做变换时跳过绘制，减轻 z-fighting 导致的发黑。
 */
public final class BeatBlockAnimatedBlocksRenderer {

	/** 实体方块绘制比线框贵，默认上限略低；仍覆盖常见舞台规模。 */
	private static final int MAX_DRAW_BLOCKS = 1024;
	private static final double REST_EPS_SQ = 1e-8;
	private static final float ANGLE_EPS = 1e-2f;
	private static final float SCALE_EPS = 1e-2f;

	private BeatBlockAnimatedBlocksRenderer() {}

	public static void render(MatrixStack matrices, VertexConsumerProvider consumers) {
		if (BeatBlock.blockAnimationEngine == null) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		var clientWorld = mc.world;
		if (clientWorld == null || mc.gameRenderer == null || mc.getBlockRenderManager() == null) {
			return;
		}
		BlockRenderView world = clientWorld;
		Map<BlockPos, AnimatedBlock> frame = BeatBlock.blockAnimationEngine.getCurrentFrameBlocks();
		if (frame == null || frame.isEmpty()) {
			return;
		}

		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		List<Map.Entry<BlockPos, AnimatedBlock>> entries = new ArrayList<>(frame.entrySet());
		entries.sort(Comparator.comparing(e -> e.getKey().asLong()));

		int n = Math.min(entries.size(), MAX_DRAW_BLOCKS);
		BlockRenderManager drm = mc.getBlockRenderManager();

		for (int i = 0; i < n; i++) {
			Map.Entry<BlockPos, AnimatedBlock> e = entries.get(i);
			BlockPos orig = e.getKey();
			AnimatedBlock ab = e.getValue();
			if (!clientWorld.isChunkLoaded(orig)) {
				continue;
			}
			BlockState state = world.getBlockState(orig);
			if (state.isAir()) {
				continue;
			}

			Vec3d anim = ab.getPosition();
			if (isRedundantWithWorldBlock(orig, ab, anim)) {
				continue;
			}

			double minX = anim.x - 0.5;
			double minY = anim.y;
			double minZ = anim.z - 0.5;

			var model = drm.getModel(state);
			Random random = Random.create(orig.asLong());
			List<BlockModelPart> parts = new ArrayList<>();
			model.addParts(random, parts);
			if (parts.isEmpty()) {
				continue;
			}

			VertexConsumer buffer = consumers.getBuffer(BlockRenderLayers.getMovingBlockLayer(state));

			matrices.push();
			matrices.translate(minX - cam.x, minY - cam.y, minZ - cam.z);
			matrices.translate(0.5, 0.5, 0.5);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ab.getRotationYaw()));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ab.getRotationPitch()));
			float s = ab.getScale();
			matrices.scale(s, s, s);
			matrices.translate(-0.5, -0.5, -0.5);

			// cull=false：避免与邻近几何相交时背面被裁切发暗
			drm.renderBlock(state, orig, world, matrices, buffer, false, parts);
			matrices.pop();
		}
	}

	/** 与世界中该格方块重合且无旋转/缩放时不再叠一层，避免深度冲突发黑。 */
	private static boolean isRedundantWithWorldBlock(BlockPos orig, AnimatedBlock ab, Vec3d anim) {
		Vec3d rest = new Vec3d(orig.getX() + 0.5, orig.getY(), orig.getZ() + 0.5);
		if (anim.squaredDistanceTo(rest) > REST_EPS_SQ) {
			return false;
		}
		if (Math.abs(ab.getRotationYaw()) > ANGLE_EPS || Math.abs(ab.getRotationPitch()) > ANGLE_EPS) {
			return false;
		}
		return Math.abs(ab.getScale() - 1f) <= SCALE_EPS;
	}
}
