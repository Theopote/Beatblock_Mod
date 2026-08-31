package com.beatblock.audio.analysis.structure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 novelty 与自相似矩阵检测 phrase / section 边界。
 */
public final class StructureBoundaryDetector {

	private static final double MIN_PHRASE_SECONDS = 4.0;
	private static final double MIN_SECTION_SECONDS = 8.0;

	private StructureBoundaryDetector() {}

	public static List<Double> detectPhraseBoundaries(
		double[] novelty,
		List<StructureFeatureFrame> frames,
		double barDurationSeconds
	) {
		double minGap = Math.max(MIN_PHRASE_SECONDS, barDurationSeconds * 2.0);
		return detectPeaks(novelty, frames, minGap, 0.55);
	}

	public static List<Double> detectSectionBoundaries(
		double[] featureNovelty,
		double[] checkerboardNovelty,
		List<StructureFeatureFrame> frames,
		double barDurationSeconds,
		double durationSeconds
	) {
		double minGap = Math.max(MIN_SECTION_SECONDS, barDurationSeconds * 8.0);
		double[] combined = combineNovelty(featureNovelty, checkerboardNovelty);
		List<Double> peaks = detectPeaks(combined, frames, minGap, 0.65);
		return finalizeBoundaries(peaks, durationSeconds, barDurationSeconds);
	}

	private static double[] combineNovelty(double[] featureNovelty, double[] checkerboardNovelty) {
		int n = Math.max(featureNovelty != null ? featureNovelty.length : 0,
			checkerboardNovelty != null ? checkerboardNovelty.length : 0);
		double[] combined = new double[n];
		for (int i = 0; i < n; i++) {
			double a = featureNovelty != null && i < featureNovelty.length ? featureNovelty[i] : 0;
			double b = checkerboardNovelty != null && i < checkerboardNovelty.length ? checkerboardNovelty[i] : 0;
			combined[i] = a * 0.45 + b * 0.55;
		}
		return combined;
	}

	private static List<Double> detectPeaks(
		double[] curve,
		List<StructureFeatureFrame> frames,
		double minGapSeconds,
		double quantile
	) {
		if (curve == null || curve.length == 0 || frames == null || frames.isEmpty()) {
			return List.of(0.0);
		}
		double threshold = NoveltyCurve.percentile(curve, quantile);
		List<Integer> peakIndices = new ArrayList<>();
		for (int i = 1; i < curve.length - 1; i++) {
			if (curve[i] >= threshold && curve[i] >= curve[i - 1] && curve[i] >= curve[i + 1]) {
				peakIndices.add(i);
			}
		}
		peakIndices.sort((a, b) -> Double.compare(curve[b], curve[a]));
		List<Double> selected = new ArrayList<>();
		selected.add(0.0);
		for (int index : peakIndices) {
			double time = frames.get(index).timeSeconds();
			if (isFarEnough(selected, time, minGapSeconds)) {
				selected.add(time);
			}
		}
		selected.sort(Double::compare);
		return selected;
	}

	private static List<Double> finalizeBoundaries(
		List<Double> boundaries,
		double durationSeconds,
		double barDurationSeconds
	) {
		Set<Double> unique = new LinkedHashSet<>();
		unique.add(0.0);
		for (double boundary : boundaries) {
			if (boundary <= 0 || boundary >= durationSeconds) continue;
			double snapped = snap(boundary, barDurationSeconds);
			unique.add(snapped);
		}
		unique.add(durationSeconds);
		List<Double> sorted = new ArrayList<>(unique);
		sorted.sort(Double::compare);
		return mergeCloseBoundaries(sorted, Math.max(2.0, barDurationSeconds));
	}

	private static boolean isFarEnough(List<Double> selected, double time, double minGapSeconds) {
		for (double existing : selected) {
			if (Math.abs(existing - time) < minGapSeconds) return false;
		}
		return true;
	}

	private static double snap(double timeSeconds, double barDurationSeconds) {
		if (barDurationSeconds <= 0) return timeSeconds;
		return Math.round(timeSeconds / barDurationSeconds) * barDurationSeconds;
	}

	private static List<Double> mergeCloseBoundaries(List<Double> boundaries, double minDistance) {
		if (boundaries.size() <= 2) return boundaries;
		List<Double> merged = new ArrayList<>();
		merged.add(boundaries.get(0));
		for (int i = 1; i < boundaries.size() - 1; i++) {
			double prev = merged.get(merged.size() - 1);
			double cur = boundaries.get(i);
			if (cur - prev >= minDistance) merged.add(cur);
		}
		double last = boundaries.get(boundaries.size() - 1);
		if (last - merged.get(merged.size() - 1) >= minDistance * 0.5) {
			merged.add(last);
		} else {
			merged.set(merged.size() - 1, last);
		}
		return merged;
	}
}
