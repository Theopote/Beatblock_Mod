package com.beatblock.timeline;

/**
 * 频段事件：预处理音频得到的能量点，用于时间线 Low/Mid/High 行可视化与自动编排。
 * 时间单位与 Timeline 一致（秒）。
 */
public final class FrequencyEvent {

	private final double timeSeconds;
	private final FrequencyBand band;
	private final float energy;

	public FrequencyEvent(double timeSeconds, FrequencyBand band, float energy) {
		this.timeSeconds = timeSeconds;
		this.band = band != null ? band : FrequencyBand.LOW;
		this.energy = Math.max(0f, Math.min(1f, energy));
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}

	public FrequencyBand getBand() {
		return band;
	}

	public float getEnergy() {
		return energy;
	}
}
