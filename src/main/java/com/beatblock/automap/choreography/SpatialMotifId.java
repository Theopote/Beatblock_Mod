package com.beatblock.automap.choreography;

import org.jspecify.annotations.Nullable;

/**
 * 跨 {@link com.beatblock.engine.RuntimeStageObject} 的空间编排原语。
 * <p>
 * 与单对象 {@code animationTypeId}（bounce/pulse）正交：motif 描述对象之间的时序与空间关系，
 * {@link SpatialMotifPhrase#primitiveId()} 描述每个对象局部的运动原语。
 */
public enum SpatialMotifId {
	CASCADE,
	CONVERGE,
	DIVERGE,
	WAVE,
	ALTERNATE,
	ECHO,
	RIPPLE,
	SWEEP,
	CHASE,
	SPIRAL,
	GATHER,
	EXPLODE;

	public static SpatialMotifId fromValue(@Nullable Object value) {
		if (value == null) return CASCADE;
		try {
			return SpatialMotifId.valueOf(String.valueOf(value).trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return CASCADE;
		}
	}
}
