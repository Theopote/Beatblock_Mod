package com.beatblock.timeline;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 舞台事件时间索引：对已按 {@link TimelineAnimationEvent#getTimeSeconds()} 升序排列的列表
 * 做二分定位，供播放调度双指针与 seek 使用。
 */
public final class StageEventIndex {

	private StageEventIndex() {}

	/**
	 * 第一个满足 {@code timeSeconds >= time} 的下标；若全部更早则返回 {@code size}。
	 */
	public static int lowerBound(@Nullable List<TimelineAnimationEvent> events, double time) {
		if (events == null || events.isEmpty()) {
			return 0;
		}
		int lo = 0;
		int hi = events.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (events.get(mid).getTimeSeconds() < time) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	/**
	 * 第一个满足 {@code timeSeconds > time} 的下标；若全部 ≤ time 则返回 {@code size}。
	 */
	public static int upperBound(@Nullable List<TimelineAnimationEvent> events, double time) {
		if (events == null || events.isEmpty()) {
			return 0;
		}
		int lo = 0;
		int hi = events.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (events.get(mid).getTimeSeconds() <= time) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	/**
	 * 将游标对齐到「已调度到 {@code currentTime}」之后的位置：
	 * 返回第一个尚未到期（time &gt; currentTime + epsilon）的下标。
	 */
	public static int cursorAfterTime(
		@NonNull List<TimelineAnimationEvent> events,
		double currentTime,
		double epsilon
	) {
		return upperBound(events, currentTime + epsilon);
	}
}
