package com.beatblock.timeline.generation;

import java.util.Locale;
import java.util.Map;

/**
 * STEP 序列时间戳来源（阶段 4.5 / 4.6 跑酷验收）。
 */
public enum PacingMode {
	BEAT_GRID,
	FIXED_INTERVAL,
	DISTANCE;

	public static PacingMode fromValue(Object raw) {
		if (raw == null) return BEAT_GRID;
		String s = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
		if (s.isEmpty()) return BEAT_GRID;
		return switch (s) {
			case "DISTANCE", "DIST" -> DISTANCE;
			case "FIXED", "FIXED_INTERVAL", "INTERVAL" -> FIXED_INTERVAL;
			case "BEAT", "BEAT_GRID", "GRID", "AUTO" -> BEAT_GRID;
			default -> BEAT_GRID;
		};
	}

	public static PacingMode fromParams(Map<String, Object> params, boolean hasReferenceBeats) {
		if (params != null && params.containsKey("pacingMode")) {
			PacingMode explicit = fromValue(params.get("pacingMode"));
			if (explicit == DISTANCE) return DISTANCE;
			if (explicit == FIXED_INTERVAL) return FIXED_INTERVAL;
			if (explicit == BEAT_GRID) return BEAT_GRID;
		}
		return hasReferenceBeats ? BEAT_GRID : FIXED_INTERVAL;
	}
}
