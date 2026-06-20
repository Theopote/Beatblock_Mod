package com.beatblock.engine.influence;

/**
 * 单条影响通道：维度 + 路径 + 曲线 + 起止值。
 */
public record ChannelSpec(
	InfluenceDimension dimension,
	PathKind path,
	CurveKind curve,
	float from,
	float to,
	boolean enabled,
	DurationPolicy durationPolicy
) {
	public ChannelSpec {
		durationPolicy = durationPolicy != null ? durationPolicy : DurationPolicy.fullDuration();
	}

	public static ChannelSpec enabled(
		InfluenceDimension dimension,
		PathKind path,
		CurveKind curve,
		float from,
		float to
	) {
		return new ChannelSpec(dimension, path, curve, from, to, true, DurationPolicy.fullDuration());
	}

	public float sample(float tNormalized) {
		float factor = CurveLibrary.sample(curve, tNormalized);
		return from + (to - from) * factor;
	}

	public float sampleMagnitude(float tNormalized, float energy) {
		return sample(tNormalized) * Math.max(0f, energy);
	}
}
