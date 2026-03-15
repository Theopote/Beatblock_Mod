package com.beatblock.audio.analysis;

/**
 * 节拍检测输出：时间戳与强度，供 Timeline 与自动编排使用。
 */
public final class DetectedBeat {

	private final double timeSeconds;
	private final float strength;

	public DetectedBeat(double timeSeconds, float strength) {
		this.timeSeconds = timeSeconds;
		this.strength = Math.max(0f, Math.min(1f, strength));
	}

	public double getTimeSeconds() { return timeSeconds; }
	public float getStrength() { return strength; }
}
