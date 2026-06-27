package com.beatblock.timeline;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * 时间线动画事件的动作模式。
 */
public enum TimelineAnimationActionMode {
	ANIMATE,
	PLACE,
	CLEAR,
	BUILD;

	public static TimelineAnimationActionMode fromValue(@Nullable Object value) {
		if (value == null) return ANIMATE;
		try {
			return TimelineAnimationActionMode.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return ANIMATE;
		}
	}
}