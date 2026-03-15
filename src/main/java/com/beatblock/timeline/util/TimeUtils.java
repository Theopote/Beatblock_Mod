package com.beatblock.timeline.util;

/**
 * 时间线时间↔屏幕坐标等通用计算。
 */
public final class TimeUtils {

	private TimeUtils() {}

	/** 时间 → 屏幕 X（相对内容区左侧） */
	public static float timeToScreen(double timeSeconds, double viewStartSeconds, float zoom) {
		return (float) (timeSeconds - viewStartSeconds) * zoom;
	}

	/** 屏幕 X → 时间 */
	public static double screenToTime(float screenX, double viewStartSeconds, float zoom) {
		return screenX / zoom + viewStartSeconds;
	}

	/** 根据可见范围与宽度计算合适网格步长（秒） */
	public static double gridStep(double viewStart, double viewEnd, float zoom) {
		double range = Math.max(0.01, viewEnd - viewStart);
		if (range > 30) return 5;
		if (range > 10) return 2;
		if (range > 2) return 1;
		return 0.5;
	}
}
