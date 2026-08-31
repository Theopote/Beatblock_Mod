package com.beatblock.audio.analysis.structure;

import java.util.Arrays;
import java.util.List;

/**
 * 特征差分 novelty 曲线（Foote 风格简化版）。
 */
public final class NoveltyCurve {

	private NoveltyCurve() {}

	public static double[] compute(List<StructureFeatureFrame> frames) {
		if (frames == null || frames.isEmpty()) return new double[0];
		double[] novelty = new double[frames.size()];
		for (int i = 1; i < frames.size(); i++) {
			novelty[i] = distance(frames.get(i - 1), frames.get(i));
		}
		return novelty;
	}

	public static double[] smooth(double[] novelty, List<StructureFeatureFrame> frames, double windowSeconds) {
		if (novelty == null || novelty.length == 0 || frames == null || frames.isEmpty()) {
			return novelty != null ? novelty.clone() : new double[0];
		}
		double[] smoothed = new double[novelty.length];
		for (int i = 0; i < novelty.length; i++) {
			double center = frames.get(i).timeSeconds();
			double sum = 0;
			int count = 0;
			for (int j = 0; j < novelty.length; j++) {
				if (Math.abs(frames.get(j).timeSeconds() - center) <= windowSeconds) {
					sum += novelty[j];
					count++;
				}
			}
			smoothed[i] = count > 0 ? sum / count : novelty[i];
		}
		return smoothed;
	}

	private static double distance(StructureFeatureFrame a, StructureFeatureFrame b) {
		float[] va = a.toVector();
		float[] vb = b.toVector();
		double sum = 0;
		for (int i = 0; i < va.length; i++) {
			double d = va[i] - vb[i];
			sum += d * d;
		}
		return Math.sqrt(sum);
	}

	public static double percentile(double[] values, double quantile) {
		if (values == null || values.length == 0) return 0;
		double[] copy = Arrays.copyOf(values, values.length);
		Arrays.sort(copy);
		int index = (int) Math.floor(Math.max(0, Math.min(1, quantile)) * (copy.length - 1));
		return copy[index];
	}
}
