package com.beatblock.audio.analysis;

/**
 * 音乐可视化常用三段：Low 20-250Hz, Mid 250-4000Hz, High 4000-16000Hz。
 * 由 FFTAnalyzer 从频谱 bin 求和得到。
 */
public final class FrequencyBands {

	public static final float LOW_HZ_MAX = 250f;
	public static final float MID_HZ_MIN = 250f;
	public static final float MID_HZ_MAX = 4000f;
	public static final float HIGH_HZ_MIN = 4000f;
	public static final float HIGH_HZ_MAX = 16000f;

	private final double timeSeconds;
	private final float low;
	private final float mid;
	private final float high;

	public FrequencyBands(double timeSeconds, float low, float mid, float high) {
		this.timeSeconds = timeSeconds;
		this.low = Math.max(0f, low);
		this.mid = Math.max(0f, mid);
		this.high = Math.max(0f, high);
	}

	public double getTimeSeconds() { return timeSeconds; }
	public float getLow() { return low; }
	public float getMid() { return mid; }
	public float getHigh() { return high; }
}
