package com.beatblock.automap.choreography;

import org.jspecify.annotations.Nullable;

/**
 * 单个音乐段落的编舞编辑覆盖：启用开关、动画类型、密度门槛、时间偏移、能量缩放、
 * 空间 motif 与语法短语（跨目标编排）覆盖。
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
	@Nullable SpatialMotifId spatialMotifIdOverride,
	@Nullable Integer grammarTriggerIntervalOverride,
	@Nullable Double grammarStaggerStepOverride,
	@Nullable String grammarIntensityCurveOverride,
	@Nullable String grammarVariationOverride
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
			null,
			null,
			null,
			null,
			null
		);
	}

	public SectionEditProfile(
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
		this(
			sectionIndex,
			motionEnabled,
			cameraEnabled,
			vfxEnabled,
			motionAnimationTypeOverride,
			densityThresholdOverride,
			timeOffsetSeconds,
			energyScale,
			spatialMotifEnabled,
			spatialMotifIdOverride,
			null,
			null,
			null,
			null
		);
	}

	public SectionEditProfile {
		sectionIndex = Math.max(0, sectionIndex);
		energyScale = Math.max(0f, energyScale);
		motionAnimationTypeOverride = blankToNull(motionAnimationTypeOverride);
		grammarIntensityCurveOverride = blankToNull(grammarIntensityCurveOverride);
		grammarVariationOverride = blankToNull(grammarVariationOverride);
		if (grammarTriggerIntervalOverride != null) {
			grammarTriggerIntervalOverride = Math.max(1, grammarTriggerIntervalOverride);
		}
		if (grammarStaggerStepOverride != null) {
			grammarStaggerStepOverride = Math.max(0.0, grammarStaggerStepOverride);
		}
	}

	public static SectionEditProfile defaults(int sectionIndex) {
		return new SectionEditProfile(
			sectionIndex, true, true, true, null, null, 0.0, 1f,
			true, null, null, null, null, null
		);
	}

	public SectionEditProfile withMotionEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, enabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withCameraEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, enabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withVfxEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, enabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withMotionAnimationType(@Nullable String animationTypeId) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			animationTypeId, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withDensityThreshold(@Nullable Double threshold) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, threshold, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withTimeOffsetSeconds(double offset) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, offset, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withEnergyScale(float scale) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, scale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withSectionIndex(int index) {
		return new SectionEditProfile(
			index, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withSpatialMotifEnabled(boolean enabled) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			enabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withSpatialMotifId(@Nullable SpatialMotifId motifId) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			true, motifId,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withSpatialMotifAuto() {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			true, null,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withGrammarTriggerInterval(@Nullable Integer interval) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			interval, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withGrammarStaggerStep(@Nullable Double stepSeconds) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, stepSeconds,
			grammarIntensityCurveOverride, grammarVariationOverride
		);
	}

	public SectionEditProfile withGrammarIntensityCurve(@Nullable String curve) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			blankToNull(curve), grammarVariationOverride
		);
	}

	public SectionEditProfile withGrammarVariation(@Nullable String variation) {
		return new SectionEditProfile(
			sectionIndex, motionEnabled, cameraEnabled, vfxEnabled,
			motionAnimationTypeOverride, densityThresholdOverride, timeOffsetSeconds, energyScale,
			spatialMotifEnabled, spatialMotifIdOverride,
			grammarTriggerIntervalOverride, grammarStaggerStepOverride,
			grammarIntensityCurveOverride, blankToNull(variation)
		);
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
