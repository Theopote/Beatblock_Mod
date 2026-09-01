package com.beatblock.automap;

import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.TimingSnapDefaults;
import org.jspecify.annotations.Nullable;

/**
 * 自动映射规则：匹配特征轨 key（如 low/kick、mid/snare、high/hihat）与能量阈值。
 */
public final class AutoMapRule {

	private final String featureKey;
	private final float minEnergy;
	private final String animationTypeId;
	private final double durationSeconds;
	private final boolean useEnergyForHeight;
	private final float heightMultiplier;
	/** &lt;= 0 表示使用 {@link AutoMapConfig#getMinGapSeconds()} 全局默认。 */
	private final double minGapSeconds;
	/** 非空时优先于 config 的 per-feature 映射。 */
	private final @Nullable String targetObjectId;
	/** 非空时覆盖 {@link TimingSnapDefaults#forFeatureKey(String)}。 */
	private final @Nullable ChoreographyTimingSnap timingSnap;

	public AutoMapRule(String featureKey, float minEnergy, String animationTypeId,
	                   double durationSeconds, boolean useEnergyForHeight, float heightMultiplier) {
		this(featureKey, minEnergy, animationTypeId, durationSeconds, useEnergyForHeight, heightMultiplier, 0.0, null, null);
	}

	public AutoMapRule(String featureKey, float minEnergy, String animationTypeId,
	                   double durationSeconds, boolean useEnergyForHeight, float heightMultiplier,
	                   double minGapSeconds, @Nullable String targetObjectId) {
		this(featureKey, minEnergy, animationTypeId, durationSeconds, useEnergyForHeight, heightMultiplier,
			minGapSeconds, targetObjectId, null);
	}

	public AutoMapRule(String featureKey, float minEnergy, String animationTypeId,
	                   double durationSeconds, boolean useEnergyForHeight, float heightMultiplier,
	                   double minGapSeconds, @Nullable String targetObjectId,
	                   @Nullable ChoreographyTimingSnap timingSnap) {
		this.featureKey = featureKey != null && !featureKey.isBlank() ? featureKey : "low";
		this.minEnergy = Math.max(0f, Math.min(1f, minEnergy));
		this.animationTypeId = animationTypeId != null ? animationTypeId : "bounce";
		this.durationSeconds = Math.max(0.01, durationSeconds);
		this.useEnergyForHeight = useEnergyForHeight;
		this.heightMultiplier = heightMultiplier;
		this.minGapSeconds = minGapSeconds > 0 ? minGapSeconds : 0.0;
		this.targetObjectId = targetObjectId != null && !targetObjectId.isBlank() ? targetObjectId : null;
		this.timingSnap = timingSnap;
	}

	public String getFeatureKey() {
		return featureKey;
	}

	public float getMinEnergy() {
		return minEnergy;
	}

	public String getAnimationTypeId() {
		return animationTypeId;
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public boolean isUseEnergyForHeight() {
		return useEnergyForHeight;
	}

	public float getHeightMultiplier() {
		return heightMultiplier;
	}

	public double getMinGapSeconds() {
		return minGapSeconds;
	}

	public @Nullable String getTargetObjectId() {
		return targetObjectId;
	}

	public @Nullable ChoreographyTimingSnap getTimingSnap() {
		return timingSnap;
	}

	public ChoreographyTimingSnap resolveTimingSnap() {
		return timingSnap != null ? timingSnap : TimingSnapDefaults.forFeatureKey(featureKey);
	}

	public double resolveMinGap(double configDefaultMinGap) {
		return minGapSeconds > 0 ? minGapSeconds : Math.max(0.0, configDefaultMinGap);
	}
}
