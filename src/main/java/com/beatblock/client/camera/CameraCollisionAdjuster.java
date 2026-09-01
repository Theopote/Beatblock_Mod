package com.beatblock.client.camera;

import com.beatblock.automap.camera.CameraCollisionPolicy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** 根据 {@link CameraCollisionPolicy} 调整摄像机采样位置。 */
final class CameraCollisionAdjuster {

	private CameraCollisionAdjuster() {}

	static TimelineCameraEvaluator.CameraSample adjust(
		TimelineCameraEvaluator.CameraSample sample,
		CameraCollisionPolicy policy
	) {
		if (sample == null || policy == null || policy == CameraCollisionPolicy.IGNORE) {
			return sample;
		}
		Vec3d position = sample.position();
		return switch (policy) {
			case AVOID_BLOCKS -> new TimelineCameraEvaluator.CameraSample(
				avoidBlocks(position), sample.yawDeg(), sample.pitchDeg());
			case CLIP_TO_BOUNDS -> new TimelineCameraEvaluator.CameraSample(
				clipToBounds(position), sample.yawDeg(), sample.pitchDeg());
			default -> sample;
		};
	}

	private static Vec3d avoidBlocks(Vec3d position) {
		World world = currentWorld();
		if (world == null) return position;
		BlockPos block = BlockPos.ofFloored(position);
		if (!isSolid(world, block)) return position;
		for (int dy = 1; dy <= 6; dy++) {
			BlockPos candidate = block.up(dy);
			if (!isSolid(world, candidate)) {
				return new Vec3d(position.x, candidate.getY() + 0.1, position.z);
			}
		}
		return position.add(0, 1.0, 0);
	}

	private static Vec3d clipToBounds(Vec3d position) {
		World world = currentWorld();
		if (world == null) return position;
		double minY = world.getBottomY() + 1.0;
		double maxY = world.getTopYInclusive() - 1.0;
		return new Vec3d(position.x, Math.max(minY, Math.min(maxY, position.y)), position.z);
	}

	private static boolean isSolid(World world, BlockPos pos) {
		return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	private static World currentWorld() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null ? client.world : null;
	}
}
