package com.beatblock.timeline;

import java.util.Arrays;

/**
 * 预计算波形数据，供时间线绘制。不每帧计算，避免性能问题。
 */
public final class WaveformData {

	private final float[] samples;
	private final double durationSeconds;
	private final int sampleRate;

	public WaveformData(float[] samples, double durationSeconds, int sampleRate) {
		this.samples = samples != null ? Arrays.copyOf(samples, samples.length) : new float[0];
		this.durationSeconds = Math.max(0, durationSeconds);
		this.sampleRate = Math.max(1, sampleRate);
	}

	public float[] getSamples() {
		return Arrays.copyOf(samples, samples.length);
	}

	public int getSampleCount() {
		return samples.length;
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	/** 给定时间（秒）对应的采样索引 */
	public int timeToIndex(double timeSeconds) {
		if (samples.length == 0 || durationSeconds <= 0) return 0;
		double t = Math.max(0, Math.min(1, timeSeconds / durationSeconds));
		return (int) (t * (samples.length - 1));
	}

	/** 给定索引的幅值（已归一化 -1..1 或 0..1 视预处理而定） */
	public float getSample(int index) {
		if (samples.length == 0) return 0;
		int i = Math.max(0, Math.min(index, samples.length - 1));
		return samples[i];
	}
}
