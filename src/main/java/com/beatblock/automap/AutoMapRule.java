package com.beatblock.automap;

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

	public AutoMapRule(String featureKey, float minEnergy, String animationTypeId,
	                   double durationSeconds, boolean useEnergyForHeight, float heightMultiplier) {
		this.featureKey = featureKey != null && !featureKey.isBlank() ? featureKey : "low";
		this.minEnergy = Math.max(0f, Math.min(1f, minEnergy));
		this.animationTypeId = animationTypeId != null ? animationTypeId : "bounce";
		this.durationSeconds = Math.max(0.01, durationSeconds);
		this.useEnergyForHeight = useEnergyForHeight;
		this.heightMultiplier = heightMultiplier;
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
}
