package com.beatblock.audio.analysis;

import com.beatblock.audio.RealFFT;

import java.util.ArrayList;
import java.util.List;

/**
 * 频谱分析：FFT 窗滑动，得到每帧频谱幅度及低/中/高频段能量。
 * 频段：Low 20-250Hz, Mid 250-4000Hz, High 4000-16000Hz。
 */
public final class FFTAnalyzer {

	public static final int FFT_SIZE = 1024;
	public static final int FFT_STEP = 512;

	private final RealFFT fft = new RealFFT(FFT_SIZE);
	private final float[] power = new float[FFT_SIZE / 2 + 1];

	/**
	 * 对 buffer 做 FFT 分析，返回每帧的频谱与频段。
	 */
	public FFTAnalysisResult analyze(AudioBuffer buffer) {
		if (buffer == null || buffer.getLength() < FFT_SIZE) {
			return new FFTAnalysisResult(List.of(), List.of(), buffer != null ? buffer.getSampleRate() : 0);
		}
		float[] samples = buffer.getSamples();
		int sampleRate = buffer.getSampleRate();
		int numWindows = (samples.length - FFT_SIZE) / FFT_STEP + 1;
		List<SpectrumFrame> spectra = new ArrayList<>();
		List<FrequencyBands> bands = new ArrayList<>();
		int lowEnd = hzToBin(FrequencyBands.LOW_HZ_MAX, sampleRate);
		int midEnd = hzToBin(FrequencyBands.MID_HZ_MAX, sampleRate);
		int highEnd = hzToBin(FrequencyBands.HIGH_HZ_MAX, sampleRate);
		lowEnd = Math.min(lowEnd, power.length - 1);
		midEnd = Math.min(midEnd, power.length - 1);
		highEnd = Math.min(highEnd, power.length - 1);

		for (int w = 0; w < numWindows; w++) {
			int offset = w * FFT_STEP;
			fft.powerSpectrum(samples, offset, power);
			double time = (offset + FFT_SIZE / 2.0) / sampleRate;
			spectra.add(new SpectrumFrame(power.clone(), time));
			float low = sumPower(0, lowEnd);
			float mid = sumPower(lowEnd + 1, midEnd);
			float high = sumPower(midEnd + 1, highEnd);
			bands.add(new FrequencyBands(time, low, mid, high));
		}
		return new FFTAnalysisResult(spectra, bands, sampleRate);
	}

	private int hzToBin(float hz, int sampleRate) {
		return (int) (hz * FFT_SIZE / sampleRate);
	}

	private float sumPower(int from, int to) {
		float s = 0;
		for (int i = from; i <= to && i < power.length; i++) {
			s += power[i];
		}
		return s;
	}

	public static final class FFTAnalysisResult {
		private final List<SpectrumFrame> spectra;
		private final List<FrequencyBands> bands;
		private final int sampleRate;

		public FFTAnalysisResult(List<SpectrumFrame> spectra, List<FrequencyBands> bands, int sampleRate) {
			this.spectra = spectra != null ? List.copyOf(spectra) : List.of();
			this.bands = bands != null ? List.copyOf(bands) : List.of();
			this.sampleRate = sampleRate;
		}

		public List<SpectrumFrame> getSpectra() { return spectra; }
		public List<FrequencyBands> getBands() { return bands; }
		public int getSampleRate() { return sampleRate; }
	}
}
