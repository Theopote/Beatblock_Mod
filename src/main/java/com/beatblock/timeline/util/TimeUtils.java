package com.beatblock.timeline.util;

/**
 * 时间线无状态工具：仅保留不依赖 ViewState 实例的计算。
 */
public final class TimeUtils {

	private TimeUtils() {}

	/** 根据可见范围与宽度计算合适网格步长（秒） */
	public static double gridStep(double viewStart, double viewEnd, float zoom) {
		double range = Math.max(0.01, viewEnd - viewStart);
		if (range > 30) return 5;
		if (range > 10) return 2;
		if (range > 2) return 1;
		return 0.5;
	}
}
