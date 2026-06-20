package com.beatblock.timeline.generation;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 按有序方块路径的 3D 欧氏距离累加计算每块绝对时间（跑酷踩点，4.6 验收）。
 * <p>
 * 相邻块间隔 {@code max(minGapSeconds, distanceBlocks * secondsPerBlockUnit)}。
 */
public final class DistancePacing implements PacingStrategy {

	static final DistancePacing INSTANCE = new DistancePacing();

	public static final double DEFAULT_SECONDS_PER_BLOCK_UNIT = 0.12;
	public static final double DEFAULT_MIN_GAP_SECONDS = 0.05;

	private DistancePacing() {}

	@Override
	public List<Double> computeTimestamps(PacingRequest request) {
		if (request == null || request.slotCount() <= 0) return List.of();
		List<BlockPos> blocks = request.orderedBlocks();
		if (blocks == null || blocks.isEmpty()) {
			return List.of(request.anchorTimeSeconds());
		}
		return computeBlockTimestamps(
			blocks,
			request.anchorTimeSeconds(),
			request.startImmediately(),
			request.secondsPerBlockUnit(),
			request.minGapSeconds()
		);
	}

	public static List<Double> computeBlockTimestamps(
		List<BlockPos> orderedBlocks,
		double anchorTimeSeconds,
		boolean startImmediately,
		double secondsPerBlockUnit,
		double minGapSeconds
	) {
		if (orderedBlocks == null || orderedBlocks.isEmpty()) return List.of();
		double pace = secondsPerBlockUnit > 0 ? secondsPerBlockUnit : DEFAULT_SECONDS_PER_BLOCK_UNIT;
		double minGap = minGapSeconds >= 0 ? minGapSeconds : DEFAULT_MIN_GAP_SECONDS;

		List<Double> times = new ArrayList<>(orderedBlocks.size());
		double t = Math.max(0.0, anchorTimeSeconds);
		if (startImmediately) {
			times.add(t);
		}
		while (times.size() < orderedBlocks.size()) {
			int i = times.size();
			BlockPos prev = orderedBlocks.get(i - 1);
			BlockPos curr = orderedBlocks.get(i);
			double gap = Math.max(minGap, blockDistance(prev, curr) * pace);
			t += gap;
			times.add(t);
		}
		return times;
	}

	static double blockDistance(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dy = a.getY() - b.getY();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
