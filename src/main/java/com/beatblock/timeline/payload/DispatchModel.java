package com.beatblock.timeline.payload;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** 动画派发模型：瞬时爆发 vs 按序逐步。 */
public enum DispatchModel {
	BURST,
	STEP;

	public static DispatchModel fromValue(@Nullable Object raw) {
		if (raw == null) return BURST;
		String s = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
		if ("STEP".equals(s)) return STEP;
		return BURST;
	}
}
