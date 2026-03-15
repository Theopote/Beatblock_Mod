package com.beatblock.audio.analysis;

import com.beatblock.audio.RealFFT;

import java.util.ArrayList;
import java.util.List;

/**
 * 节拍检测：Spectral Flux — 相邻 FFT 帧的频谱变化，超过局部阈值记为 beat。
 * 流程：计算 flux = sum(max(0, spectrum[i] - prevSpectrum[i]))；局部平均为 threshold；flux > threshold 则 beat。
 */
public final class BeatDetector {

	public static final int FFT_SIZE = 1024;
	public static final int FFT_STEP = 512;
	/** 局部平均窗口（帧数） */
	public static final int FLUX_WINDOW = 12;
	/** 阈值乘数，越大检测越少 */
	public static final float THRESHOLD_MULTIPLIER = 1.2f;
	/** 最小节拍间隔（秒），避免连击 */
	public static final double MIN_BEAT_INTERVAL = 0.08;

	private final RealFFT fft = new RealFFT(FFT_SIZE);
	private final float[] power = new float[FFT_SIZE / 2 + 1];

	public List<DetectedBeat> detect(AudioBuffer buffer) {
		List<DetectedBeat> beats = new ArrayList<>();
		if (buffer == null || buffer.getLength() < FFT_SIZE) return beats;
		float[] samples = buffer.getSamples();
		int sampleRate = buffer.getSampleRate();
		int numWindows = (samples.length - FFT_SIZE) / FFT_STEP + 1;
		if (numWindows < 2) return beats;

		float[] prevSpectrum = null;
		float[] fluxHistory = new float[FLUX_WINDOW];
		int fluxIdx = 0;
		double lastBeatTime = -MIN_BEAT_INTERVAL * 2;

		for (int w = 0; w < numWindows; w++) {
			int offset = w * FFT_STEP;
			fft.powerSpectrum(samples, offset, power);
			double time = (offset + FFT_SIZE / 2.0) / sampleRate;

			if (prevSpectrum != null) {
				float flux = 0;
				for (int i = 0; i < power.length; i++) {
					float diff = power[i] - prevSpectrum[i];
					if (diff > 0) flux += diff;
				}
				fluxHistory[fluxIdx % FLUX_WINDOW] = flux;
				fluxIdx++;
				float mean = 0;
				int count = Math.min(fluxIdx, FLUX_WINDOW);
				for (int i = 0; i < count; i++) mean += fluxHistory[i];
				mean /= count;
				float threshold = mean * THRESHOLD_MULTIPLIER;
				if (flux > threshold && (time - lastBeatTime) >= MIN_BEAT_INTERVAL) {
					float strength = Math.min(1f, (flux - threshold) / (mean + 1e-6f) * 0.5f);
					beats.add(new DetectedBeat(time, strength));
					lastBeatTime = time;
				}
			}
			prevSpectrum = power.clone();
		}
		return beats;
	}
}
