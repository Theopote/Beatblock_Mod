package com.beatblock.timeline;

import org.jspecify.annotations.Nullable;

/**
 * Marker 来源：手工、音频分析、其它生成管线、外部导入。
 */
public enum MarkerOrigin {
	MANUAL,
	AUDIO_ANALYSIS,
	GENERATED,
	IMPORTED;

	public boolean isSystemProduced() {
		return this == AUDIO_ANALYSIS || this == GENERATED;
	}

	public static MarkerOrigin fromValue(@Nullable Object raw) {
		if (raw == null) {
			return MANUAL;
		}
		String s = String.valueOf(raw).trim();
		if (s.isEmpty()) {
			return MANUAL;
		}
		String normalized = s.toUpperCase().replace('-', '_');
		return switch (normalized) {
			case "MANUAL", "USER" -> MANUAL;
			case "AUDIO_ANALYSIS", "AUDIO", "ANALYSIS", "ANALYZED" -> AUDIO_ANALYSIS;
			case "GENERATED", "AUTO_GENERATED", "AUTOMAP", "AUTO_MAP", "LLM", "AI" -> GENERATED;
			case "IMPORTED", "IMPORT", "OSC" -> IMPORTED;
			default -> {
				try {
					yield valueOf(normalized);
				} catch (IllegalArgumentException ex) {
					yield MANUAL;
				}
			}
		};
	}
}
