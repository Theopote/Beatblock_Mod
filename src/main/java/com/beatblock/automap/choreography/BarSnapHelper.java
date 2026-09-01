package com.beatblock.automap.choreography;

/**
 * 将编舞事件时间对齐到最近的小节起点（structure v2）。
 */
public final class BarSnapHelper {

	static final double DEFAULT_TOLERANCE_SECONDS = 0.08;

	private BarSnapHelper() {}

	public static double snapToNearestBarStart(
		double timeSeconds,
		ChoreographyPlan.MusicalStructure musical,
		double toleranceSeconds
	) {
		if (musical == null || musical.bars().isEmpty() || toleranceSeconds <= 0) {
			return timeSeconds;
		}
		double bestTime = timeSeconds;
		double bestDistance = toleranceSeconds;
		for (ChoreographyPlan.BarPlan bar : musical.bars()) {
			double distance = Math.abs(timeSeconds - bar.startSeconds());
			if (distance < bestDistance) {
				bestDistance = distance;
				bestTime = bar.startSeconds();
			}
		}
		return bestTime;
	}
}
