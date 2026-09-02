package com.beatblock.automap.choreography;

import org.jspecify.annotations.Nullable;

/** 空间 motif 的传播或排序轴。 */
public enum MotifAxis {
	X,
	Z,
	RADIAL;

	public static MotifAxis fromValue(@Nullable Object value) {
		if (value == null) return X;
		try {
			return MotifAxis.valueOf(String.valueOf(value).trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return X;
		}
	}
}
