package com.beatblock.audio;

import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.WaveformData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 音频分析：从解码后的 PCM 生成波形与特征轨道（low/mid/high），写入 Timeline 参考层。
 */
public final class AudioAnalyzer {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioAnalyzer.class);

	public static final int WAVEFORM_SAMPLES = 2000;
	public static final int FFT_SIZE = 2048;
	public static final int FFT_STEP = 1024;
	public static final float ENERGY_THRESHOLD = 0.02f;
	public static final int LOW_BIN_END = 10;
	public static final int MID_BIN_END = 93;

	public static void analyzeAndFillTimeline(DecodedAudio audio, Timeline timeline) {
		if (audio == null || timeline == null) return;
		double duration = audio.getDurationSeconds();
		int sampleRate = audio.getSampleRate();
		float[] samples = audio.getSamples();
		if (samples.length == 0) return;

		timeline.setDurationSeconds(duration);
		timeline.setWaveform(buildWaveform(samples, sampleRate, duration));
		timeline.clearFeatureTracks();
		int featureCount = fillLegacyBandFeatureTracks(timeline, samples, sampleRate, duration);
		timeline.sortAll();
		LOGGER.info("BeatBlock: 已分析音频 duration={}s, waveform={} 点, 特征事件 {} 个",
			duration, timeline.getWaveform() != null ? timeline.getWaveform().getSampleCount() : 0,
			featureCount);
	}

	public static WaveformData buildWaveform(float[] samples, int sampleRate, double durationSeconds) {
		if (samples == null || samples.length == 0 || durationSeconds <= 0) {
			return new WaveformData(new float[0], 0, sampleRate);
		}
		int n = Math.min(WAVEFORM_SAMPLES, (int) (durationSeconds * 10));
		n = Math.max(1, n);
		float[] out = new float[n];
		int samplesPerBucket = samples.length / n;
		for (int i = 0; i < n; i++) {
			int start = i * samplesPerBucket;
			int end = i == n - 1 ? samples.length : (i + 1) * samplesPerBucket;
			float max = 0;
			for (int j = start; j < end; j++) {
				float a = Math.abs(samples[j]);
				if (a > max) max = a;
			}
			out[i] = max;
		}
		return new WaveformData(out, durationSeconds, sampleRate);
	}

	/** FFT 窗滑动，按 low/mid/high 写入 FeatureTrack 参考轨。 */
	static int fillLegacyBandFeatureTracks(Timeline timeline, float[] samples, int sampleRate, double durationSeconds) {
		if (timeline == null || samples == null || samples.length < FFT_SIZE || sampleRate <= 0) return 0;

		RealFFT fft = new RealFFT(FFT_SIZE);
		float[] power = new float[FFT_SIZE / 2 + 1];
		int numWindows = (samples.length - FFT_SIZE) / FFT_STEP + 1;
		int count = 0;

		for (int w = 0; w < numWindows; w++) {
			int offset = w * FFT_STEP;
			fft.powerSpectrum(samples, offset, power);
			float low = sumPower(power, 0, LOW_BIN_END);
			float mid = sumPower(power, LOW_BIN_END, MID_BIN_END);
			float high = sumPower(power, MID_BIN_END, power.length - 1);
			float total = low + mid + high;
			if (total < 1e-10f) continue;
			low /= total;
			mid /= total;
			high /= total;
			double timeSeconds = (offset + FFT_SIZE / 2) / (double) sampleRate;
			if (timeSeconds >= durationSeconds) break;
			if (low >= ENERGY_THRESHOLD) {
				timeline.addFeatureEvent("low", "低频", new FeatureEvent(timeSeconds, Math.min(1f, low * 2f)));
				count++;
			}
			if (mid >= ENERGY_THRESHOLD) {
				timeline.addFeatureEvent("mid", "中频", new FeatureEvent(timeSeconds, Math.min(1f, mid * 2f)));
				count++;
			}
			if (high >= ENERGY_THRESHOLD) {
				timeline.addFeatureEvent("high", "高频", new FeatureEvent(timeSeconds, Math.min(1f, high * 2f)));
				count++;
			}
		}
		return count;
	}

	private static float sumPower(float[] power, int from, int to) {
		float s = 0;
		for (int i = from; i <= to && i < power.length; i++) {
			s += power[i];
		}
		return s;
	}
}
