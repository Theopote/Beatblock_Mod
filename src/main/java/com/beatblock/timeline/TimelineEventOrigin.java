package com.beatblock.timeline;

/**
 * 第 2 层事件来源：手动编辑 vs 自动生成草稿。
 */
public enum TimelineEventOrigin {
	MANUAL,
	AUTO_GENERATED;

	public static TimelineEventOrigin fromValue(Object raw) {
		if (raw == null) return MANUAL;
		String s = String.valueOf(raw).trim();
		if (s.isEmpty()) return MANUAL;
		try {
			return valueOf(s.toUpperCase());
		} catch (IllegalArgumentException ex) {
			return MANUAL;
		}
	}
}
