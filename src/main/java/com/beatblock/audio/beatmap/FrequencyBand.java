package com.beatblock.audio.beatmap;

/** 低/中/高频段枚举。 */
public enum FrequencyBand {
	LOW, MID, HIGH;

	static FrequencyBand fromJson(String s) {
		return switch (s.toLowerCase()) {
			case "low"  -> LOW;
			case "mid"  -> MID;
			case "high" -> HIGH;
			default -> throw new IllegalArgumentException("未知频段: " + s);
		};
	}
}

