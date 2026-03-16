package com.beatblock.audio.beatmap;

/** 段落标签枚举。 */
public enum SectionLabel {
	INTRO, VERSE, CHORUS, BRIDGE, OUTRO, UNKNOWN;

	static SectionLabel fromJson(String s) {
		try {
			return valueOf(s.toUpperCase());
		} catch (IllegalArgumentException e) {
			return UNKNOWN;
		}
	}
}

