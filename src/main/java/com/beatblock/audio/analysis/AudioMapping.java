package com.beatblock.audio.analysis;

/**
 * 用户可配置的「频段 → 动画」映射，供 Smart Auto Map 生成事件。
 * 例如：Low → Jump, Mid → Wave, High → Explosion。
 */
public final class AudioMapping {

	public enum FrequencyBand {
		LOW,
		MID,
		HIGH
	}

	private final FrequencyBand band;
	private final String animationTypeId;

	public AudioMapping(FrequencyBand band, String animationTypeId) {
		this.band = band != null ? band : FrequencyBand.LOW;
		this.animationTypeId = animationTypeId != null ? animationTypeId : "jump";
	}

	public FrequencyBand getBand() { return band; }
	public String getAnimationTypeId() { return animationTypeId; }
}
