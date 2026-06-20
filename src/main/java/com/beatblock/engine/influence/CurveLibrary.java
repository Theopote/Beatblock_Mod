package com.beatblock.engine.influence;

/**
 * 归一化曲线采样与现有 {@code AnimationEffect} 共用的高阶组合公式。
 */
public final class CurveLibrary {

	private CurveLibrary() {}

	public static float clampT(float t) {
		if (t <= 0f) return 0f;
		if (t >= 1f) return 1f;
		return t;
	}

	public static float sample(CurveKind kind, float t) {
		t = clampT(t);
		if (kind == null) kind = CurveKind.LINEAR;
		return switch (kind) {
			case LINEAR -> t;
			case INVERSE_LINEAR -> 1f - t;
			case SINE_BUMP -> (float) Math.sin(t * Math.PI);
			case GRAVITY_REMAINING -> 1f - t * t;
			case CONST_ZERO -> 0f;
			case CONST_ONE -> 1f;
		};
	}

	public static float lerp(float from, float to, float factor) {
		return from + (to - from) * factor;
	}

	/** {@code t * magnitude * energy}（Rise / Drop / Explosion 径向等） */
	public static float linearProgress(float t, float magnitude, float energy) {
		return sample(CurveKind.LINEAR, t) * magnitude * Math.max(0f, energy);
	}

	/** {@code sin(tπ) * magnitude * energy}（Jump 纵向偏移） */
	public static float sineBumpMagnitude(float t, float magnitude, float energy) {
		return sample(CurveKind.SINE_BUMP, t) * magnitude * Math.max(0f, energy);
	}

	/** {@code 1 + (peakScale - 1) * sin(tπ) * energy}（Pulse 缩放） */
	public static float scaleSinePulse(float t, float peakScale, float energy) {
		float e = Math.max(0f, energy);
		return 1f + (peakScale - 1f) * sample(CurveKind.SINE_BUMP, t) * e;
	}

	/** 剩余高度 {@code h * (1 - t²) * energy}（Meteor 下落段） */
	public static float gravityRemainingHeight(float t, float height, float energy) {
		return sample(CurveKind.GRAVITY_REMAINING, t) * height * Math.max(0f, energy);
	}

	/** 远小近大：{@code scaleMin + (1 - scaleMin) * t}，{@code scaleMin = 1 - 0.5 * energy} */
	public static float meteorApproachScale(float t, float energy) {
		float e = Math.max(0f, energy);
		float scaleMin = 1f - 0.5f * e;
		return scaleMin + (1f - scaleMin) * clampT(t);
	}

	/** 横向散射包络 {@code (1 - t) * energy}（Meteor 收束用） */
	public static float scatterEnvelope(float t, float energy) {
		return sample(CurveKind.INVERSE_LINEAR, t) * Math.max(0f, energy);
	}
}
