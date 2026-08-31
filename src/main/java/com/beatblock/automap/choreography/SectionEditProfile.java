package com.beatblock.automap.choreography;

import org.jspecify.annotations.Nullable;

/**
 * 单个音乐段落的编舞编辑覆盖：启用开关、动画类型、密度门槛、时间偏移与能量缩放。
 */
public record SectionEditProfile(
	int sectionIndex,
	boolean motionEnabled,
	boolean cameraEnabled,
	boolean vfxEnabled,
	@Nullable String motionAnimationTypeOverride,
	@Nullable Double densityThresholdOverride,
	double timeOffsetSeconds,
	float energyScale
) {
	public SectionEditProfile {
		sectionIndex = Math.max(0, sectionIndex);
		energyScale = Math.max(0f, energyScale);
		motionAnimationTypeOverride = blankToNull(motionAnimationTypeOverride);
	}

	public static SectionEditProfile defaults(int sectionIndex) {
		return new SectionEditProfile(sectionIndex, true, true, true, null, null, 0.0, 1f);
	}

	public SectionEditProfile withMotionEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, enabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale
		);
	}

	public SectionEditProfile withCameraEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, enabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale
		);
	}

	public SectionEditProfile withVfxEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, enabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale
		);
	}

	public SectionEditProfile withMotionAnimationType(@Nullable String animationTypeId) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			animationTypeId, densityThresholdOverride, timeOffsetSeconds, energyScale
		);
	}

	public SectionEditProfile withDensityThreshold(@Nullable Double threshold) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, threshold, timeOffsetSeconds, energyScale
		);
	}

	public SectionEditProfile withTimeOffsetSeconds(double offset) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, offset, energyScale
		);
	}

	public SectionEditProfile withEnergyScale(float scale) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, scale
		);
	}

	public SectionEditProfile withSectionIndex(int index) {
		return new SectionEditProfile(
			index, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale
		);
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
