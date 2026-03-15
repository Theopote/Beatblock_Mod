package com.beatblock.audio.analysis;

import java.util.Arrays;

/**
 * 单帧频谱：各 bin 的幅度（或功率），以及对应的时间。
 * 频率范围 0 ~ sampleRate/2 Hz。
 */
public final class SpectrumFrame {

	private final float[] magnitudes;
	private final double timeSeconds;

	public SpectrumFrame(float[] magnitudes, double timeSeconds) {
		this.magnitudes = magnitudes != null ? Arrays.copyOf(magnitudes, magnitudes.length) : new float[0];
		this.timeSeconds = timeSeconds;
	}

	public float[] getMagnitudes() {
		return Arrays.copyOf(magnitudes, magnitudes.length);
	}

	public int getBinCount() {
		return magnitudes.length;
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}
}
