package com.beatblock.automap;

import com.beatblock.timeline.FrequencyBand;

/**
 * 单条自动编排规则：当某频段能量满足条件时，生成指定类型的动画事件。
 * 例如：IF low beat THEN ground bounce；IF mid energy THEN building wave。
 */
public final class AutoMapRule {

	private final FrequencyBand band;
	private final float minEnergy;
	private final String animationTypeId;
	private final double durationSeconds;
	private final boolean useEnergyForHeight;
	private final float heightMultiplier;

	public AutoMapRule(FrequencyBand band, float minEnergy, String animationTypeId,
	                   double durationSeconds, boolean useEnergyForHeight, float heightMultiplier) {
		this.band = band != null ? band : FrequencyBand.LOW;
		this.minEnergy = Math.max(0f, Math.min(1f, minEnergy));
		this.animationTypeId = animationTypeId != null ? animationTypeId : "bounce";
		this.durationSeconds = Math.max(0.05, durationSeconds);
		this.useEnergyForHeight = useEnergyForHeight;
		this.heightMultiplier = heightMultiplier;
	}

	public FrequencyBand getBand() {
		return band;
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
