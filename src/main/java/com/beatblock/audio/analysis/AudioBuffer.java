package com.beatblock.audio.analysis;

import java.util.Arrays;

/**
 * 分析引擎使用的 PCM 数据：float 采样、采样率、声道数、长度。
 * 常见 sampleRate=44100, channels=2（或解码为单声道 1）。
 */
public final class AudioBuffer {

	private final float[] samples;
	private final int sampleRate;
	private final int channels;
	private final int length;

	public AudioBuffer(float[] samples, int sampleRate, int channels) {
		this.samples = samples != null ? Arrays.copyOf(samples, samples.length) : new float[0];
		this.sampleRate = Math.max(1, sampleRate);
		this.channels = Math.max(1, channels);
		this.length = this.samples.length;
	}

	public AudioBuffer(float[] samples, int sampleRate) {
		this(samples, sampleRate, 1);
	}

	public float[] getSamples() {
		return Arrays.copyOf(samples, samples.length);
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public int getChannels() {
		return channels;
	}

	public int getLength() {
		return length;
	}

	public double getDurationSeconds() {
		return sampleRate > 0 ? (double) length / sampleRate / channels : 0;
	}
}
