package com.beatblock.timeline.generation;

import java.util.List;

/**
 * 生成时计算 N 个对象各自的时间戳（阶段 4.5）。
 */
public interface PacingStrategy {

	List<Double> computeTimestamps(PacingRequest request);

	static PacingStrategy beatGrid() {
		return BeatGridPacing.INSTANCE;
	}

	static PacingStrategy fixedInterval() {
		return FixedIntervalPacing.INSTANCE;
	}
}
