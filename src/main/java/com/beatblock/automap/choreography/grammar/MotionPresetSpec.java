package com.beatblock.automap.choreography.grammar;

import org.jspecify.annotations.Nullable;

/** 局部运动原语（bounce / pulse 等），与空间 pattern 正交。 */
public record MotionPresetSpec(
	String presetId,
	double durationSeconds,
	boolean useEnergyForHeight,
	float heightMultiplier
) {
	public MotionPresetSpec {
		presetId = presetId != null && !presetId.isBlank() ? presetId : "pulse";
		durationSeconds = Math.max(0.01, durationSeconds);
		heightMultiplier = Math.max(0f, heightMultiplier);
	}

	public static MotionPresetSpec bounce() {
		return new MotionPresetSpec("bounce", 0.5, true, 4f);
	}

	public static MotionPresetSpec of(@Nullable String presetId) {
		return new MotionPresetSpec(presetId != null ? presetId : "pulse", 0.5, true, 4f);
	}
}
