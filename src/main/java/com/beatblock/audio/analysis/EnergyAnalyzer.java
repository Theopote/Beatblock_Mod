package com.beatblock.audio.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * 能量计算：每窗 sum(samples^2) 或 RMS，表示该时刻音乐强度。
 */
public final class EnergyAnalyzer {

	public static final int WINDOW_SIZE = 512;
	public static final int WINDOW_STEP = 256;

	/**
	 * 对 buffer 按窗计算能量（RMS），返回 EnergyFrame 列表。
	 */
	public List<EnergyFrame> analyze(AudioBuffer buffer) {
		List<EnergyFrame> out = new ArrayList<>();
		if (buffer == null || buffer.getLength() < WINDOW_SIZE) return out;
		float[] samples = buffer.getSamples();
		int sampleRate = buffer.getSampleRate();
		for (int offset = 0; offset + WINDOW_SIZE <= samples.length; offset += WINDOW_STEP) {
			float sumSq = 0;
			for (int i = 0; i < WINDOW_SIZE; i++) {
				float s = samples[offset + i];
				sumSq += s * s;
			}
			float rms = (float) Math.sqrt(sumSq / WINDOW_SIZE);
			double time = (offset + WINDOW_SIZE / 2.0) / sampleRate;
			out.add(new EnergyFrame(time, rms));
		}
		return out;
	}
}
