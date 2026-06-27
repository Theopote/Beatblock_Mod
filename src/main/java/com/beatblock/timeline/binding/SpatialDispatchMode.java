package com.beatblock.timeline.binding;

import org.jspecify.annotations.Nullable;

/**
 * 方块动作的空间调度模式。
 */
public enum SpatialDispatchMode {
	ALL,
	SEQUENTIAL,
	RADIAL,
	RANDOM,
	SPIRAL;

	public static SpatialDispatchMode fromValue(@Nullable Object value) {
		if (value == null) return ALL;
		try {
			return SpatialDispatchMode.valueOf(String.valueOf(value).trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return ALL;
		}
	}
}
