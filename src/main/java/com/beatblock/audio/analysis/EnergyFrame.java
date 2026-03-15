package com.beatblock.audio.analysis;

/**
 * 单帧能量：表示该时刻音乐强度，可用于映射动画高度/速度。
 * energy 可为 sum(samples^2) 或 RMS。
 */
public final class EnergyFrame {

	private final double timeSeconds;
	private final float energy;

	public EnergyFrame(double timeSeconds, float energy) {
		this.timeSeconds = timeSeconds;
		this.energy = Math.max(0f, energy);
	}

	public double getTimeSeconds() { return timeSeconds; }
	public float getEnergy() { return energy; }
}
