package com.beatblock.automap.choreography;

import org.jspecify.annotations.Nullable;

/**
 * 单个音乐段落的编舞编辑覆盖：启用开关、动画类型、密度门槛、时间偏移、能量缩放与空间 motif。
 */
public record SectionEditProfile(
	int sectionIndex,
	boolean motionEnabled,
	boolean cameraEnabled,
	boolean vfxEnabled,
	@Nullable String motionAnimationTypeOverride,
	@Nullable Double densityThresholdOverride,
	double timeOffsetSeconds,
	float energyScale,
	boolean spatialMotifEnabled,
	@Nullable SpatialMotifId spatialMotifIdOverride
) {
	public SectionEditProfile(
		int sectionIndex,
		boolean motionEnabled,
		boolean cameraEnabled,
		boolean vfxEnabled,
		@Nullable String motionAnimationTypeOverride,
		@Nullable Double densityThresholdOverride,
		double timeOffsetSeconds,
		float energyScale
	) {
		this(
			sectionIndex,
			motionEnabled,
			cameraEnabled,
			vfxEnabled,
			motionAnimationTypeOverride,
			densityThresholdOverride,
			timeOffsetSeconds,
			energyScale,
			true,
			null
		);
	}

	public SectionEditProfile {
		sectionIndex = Math.max(0, sectionIndex);
		energyScale = Math.max(0f, energyScale);
		motionAnimationTypeOverride = blankToNull(motionAnimationTypeOverride);
	}

	public static SectionEditProfile defaults(int sectionIndex) {
		return new SectionEditProfile(sectionIndex, true, true, true, null, null, 0.0, 1f, true, null);
	}

	public SectionEditProfile withMotionEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, enabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride
		);
	}

	public SectionEditProfile withCameraEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, enabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride
		);
	}

	public SectionEditProfile withVfxEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, enabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride
		);
	}

	public SectionEditProfile withMotionAnimationType(@Nullable String animationTypeId) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			animationTypeId, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride
		);
	}

	public SectionEditProfile withDensityThreshold(@Nullable Double threshold) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, threshold, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride
		);
	}

	public SectionEditProfile withTimeOffsetSeconds(double offset) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, offset, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride
		);
	}

	public SectionEditProfile withEnergyScale(float scale) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, scale,
			spatialMotifEnabled, spatialMotifIdOverride
		);
	}

	public SectionEditProfile withSectionIndex(int index) {
		return new SectionEditProfile(
			index, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride
		);
	}

	public SectionEditProfile withSpatialMotifEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			enabled, spatialMotifIdOverride
		);
	}

	public SectionEditProfile withSpatialMotifId(@Nullable SpatialMotifId motifId) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			true, motifId
		);
	}

	public SectionEditProfile withSpatialMotifAuto() {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			true, null
		);
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
