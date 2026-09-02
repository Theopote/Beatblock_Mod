package com.beatblock.automap.choreography;

import org.jspecify.annotations.Nullable;

/** 多对象参与时的相位关系（对位 / 交替 / 同相）。 */
public enum MotifPhaseMode {
	IN_PHASE,
	ALTERNATE,
	COUNTERPOINT;

	public static MotifPhaseMode fromValue(@Nullable Object value) {
		if (value == null) return IN_PHASE;
		try {
			return MotifPhaseMode.valueOf(String.valueOf(value).trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return IN_PHASE;
		}
	}
}
