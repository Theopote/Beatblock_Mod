package com.beatblock.timeline.generation;

import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 节拍 / 距离排布请求：参考轨节拍点、固定 BPM 间隔，或有序方块路径距离。
 */
public record PacingRequest(
	int slotCount,
	double anchorTimeSeconds,
	boolean startImmediately,
	double[] referenceBeatTimesSeconds,
	double bpm,
	double fixedIntervalSeconds,
	List<BlockPos> orderedBlocks,
	double secondsPerBlockUnit,
	double minGapSeconds
) {
	public PacingRequest(
		int slotCount,
		double anchorTimeSeconds,
		boolean startImmediately,
		double[] referenceBeatTimesSeconds,
		double bpm,
		double fixedIntervalSeconds
	) {
		this(
			slotCount,
			anchorTimeSeconds,
			startImmediately,
			referenceBeatTimesSeconds,
			bpm,
			fixedIntervalSeconds,
			List.of(),
			DistancePacing.DEFAULT_SECONDS_PER_BLOCK_UNIT,
			DistancePacing.DEFAULT_MIN_GAP_SECONDS
		);
	}

	/**
	 * @param startImmediately 若 {@code true}，第一个 slot 在 anchor；否则第一个 slot 在 anchor 之后一个 pacing 步长（节拍 / 固定间隔 / minGap）
	 */
	public PacingRequest {
		slotCount = Math.max(0, slotCount);
		anchorTimeSeconds = Math.max(0.0, anchorTimeSeconds);
		bpm = Math.max(1.0, bpm);
		fixedIntervalSeconds = fixedIntervalSeconds > 0
			? fixedIntervalSeconds
			: 60.0 / bpm;
		referenceBeatTimesSeconds = referenceBeatTimesSeconds != null
			? referenceBeatTimesSeconds.clone()
			: new double[0];
		orderedBlocks = orderedBlocks != null ? List.copyOf(orderedBlocks) : List.of();
		secondsPerBlockUnit = secondsPerBlockUnit > 0
			? secondsPerBlockUnit
			: DistancePacing.DEFAULT_SECONDS_PER_BLOCK_UNIT;
		minGapSeconds = minGapSeconds >= 0 ? minGapSeconds : DistancePacing.DEFAULT_MIN_GAP_SECONDS;
	}
}
