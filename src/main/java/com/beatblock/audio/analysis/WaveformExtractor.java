package com.beatblock.audio.analysis;

/**
 * 波形生成：原始音频过密（如 44100 samples/s），降采样为 peaks 与 rms 供时间线显示。
 * 每 SAMPLES_PER_POINT 个采样生成一个点：peak = max(abs(samples)), rms = sqrt(mean(samples^2))。
 */
public final class WaveformExtractor {

	public static final int SAMPLES_PER_POINT = 512;

	/**
	 * 从 AudioBuffer 生成降采样波形。
	 *
	 * @param buffer 源 PCM
	 * @return peaks 与 rms 数组长度相同，为 (buffer.length / SAMPLES_PER_POINT) 个点；无数据时返回空数组。
	 */
	public static WaveformFrame[] extract(AudioBuffer buffer) {
		if (buffer == null || buffer.getLength() < SAMPLES_PER_POINT) {
			return new WaveformFrame[0];
		}
		float[] samples = buffer.getSamples();
		int n = samples.length / SAMPLES_PER_POINT;
		WaveformFrame[] out = new WaveformFrame[n];
		double timePerPoint = (double) SAMPLES_PER_POINT / buffer.getSampleRate();
		for (int i = 0; i < n; i++) {
			int start = i * SAMPLES_PER_POINT;
			float peak = 0;
			float sumSq = 0;
			for (int j = 0; j < SAMPLES_PER_POINT; j++) {
				float s = samples[start + j];
				float a = Math.abs(s);
				if (a > peak) peak = a;
				sumSq += s * s;
			}
			float rms = (float) Math.sqrt(sumSq / SAMPLES_PER_POINT);
			double time = (start + SAMPLES_PER_POINT / 2.0) / buffer.getSampleRate();
			out[i] = new WaveformFrame(time, peak, rms);
		}
		return out;
	}

	/** 单帧：时间（秒）、峰值、RMS */
	public static final class WaveformFrame {
		public final double time;
		public final float peak;
		public final float rms;

		public WaveformFrame(double time, float peak, float rms) {
			this.time = time;
			this.peak = peak;
			this.rms = rms;
		}
	}
}
