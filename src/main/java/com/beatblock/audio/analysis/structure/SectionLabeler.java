package com.beatblock.audio.analysis.structure;

import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 结合重复度、能量与 onset 密度为 section 边界打标签。
 */
public final class SectionLabeler {

	private SectionLabeler() {}

	public static List<StructuralSection> label(
		List<StructureFeatureFrame> frames,
		double[][] similarity,
		List<Double> boundaries,
		double durationSeconds
	) {
		if (boundaries == null || boundaries.isEmpty()) {
			return List.of(new StructuralSection(0, durationSeconds, SectionType.VERSE));
		}
		List<StructuralSection> sections = new ArrayList<>();
		List<float[]> segmentVectors = new ArrayList<>();

		for (int i = 0; i < boundaries.size() - 1; i++) {
			double start = boundaries.get(i);
			double end = boundaries.get(i + 1);
			if (end - start < 0.5) continue;
			int startIndex = frameIndexAt(frames, start);
			int endIndex = frameIndexAt(frames, end);
			SectionStats stats = statsFor(frames, start, end);
			float[] vector = meanVector(frames, startIndex, endIndex);
			double repetition = SelfSimilarityMatrix.maxPriorSimilarity(similarity, startIndex, endIndex);
			int similarPriorCount = countSimilarPrior(segmentVectors, vector, 0.72);
			segmentVectors.add(vector);
			SectionType type = classifySegment(
				stats, repetition, similarPriorCount, start, end, durationSeconds, sections.size());
			String label = labelFor(type, repetition, stats, sections.size());
			sections.add(new StructuralSection(start, end, type, label));
		}
		if (sections.isEmpty()) {
			sections.add(new StructuralSection(0, durationSeconds, SectionType.VERSE));
		}
		return sections;
	}

	private static SectionType classifySegment(
		SectionStats stats,
		double repetition,
		int similarPriorCount,
		double start,
		double end,
		double duration,
		int index
	) {
		double progress = start / Math.max(0.01, duration);
		boolean repeated = repetition >= 0.72 || similarPriorCount > 0;
		boolean chorusLike = repeated && similarPriorCount > 0 && stats.energy >= 0.55f && stats.onset >= 0.35f;
		boolean rising = stats.trend > 0.04f;
		boolean highEnergy = stats.energy >= 0.68f;
		boolean lowEnergy = stats.energy <= 0.28f;

		if (progress < 0.06 && similarPriorCount == 0) return SectionType.INTRO;
		if (end >= duration * 0.94) return SectionType.OUTRO;

		if (chorusLike) return SectionType.CHORUS;
		if (repeated && similarPriorCount == 0) return SectionType.VERSE;
		if (rising && stats.energy < 0.62f && !repeated) return SectionType.PRE_CHORUS;
		if (rising && stats.energy < 0.7f) return SectionType.BUILD;
		if (highEnergy && stats.onset >= 0.45f && !repeated) return SectionType.DROP;
		if (lowEnergy && stats.onset < 0.25f) return SectionType.BREAK;
		if (!repeated && index > 0 && stats.energy < 0.45f && segmentEnergyDrop(stats)) {
			return SectionType.BRIDGE;
		}
		return SectionType.VERSE;
	}

	private static boolean segmentEnergyDrop(SectionStats stats) {
		return stats.trend < 0f;
	}

	private static int countSimilarPrior(List<float[]> vectors, float[] current, double threshold) {
		int count = 0;
		for (float[] prior : vectors) {
			if (cosineSimilarity(current, prior) >= threshold) count++;
		}
		return count;
	}

	private static double cosineSimilarity(float[] a, float[] b) {
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			normA += a[i] * a[i];
			normB += b[i] * b[i];
		}
		if (normA < 1e-9 || normB < 1e-9) return 0;
		return dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}

	private static String labelFor(SectionType type, double repetition, SectionStats stats, int index) {
		return switch (type) {
			case CHORUS -> repetition >= 0.8 ? "chorus" : "chorus_" + (index + 1);
			case VERSE -> "verse";
			case PRE_CHORUS -> "pre-chorus";
			case BRIDGE -> "bridge";
			case BUILD -> "build";
			case DROP -> "drop";
			case BREAK -> "breakdown";
			case INTRO -> "intro";
			case OUTRO -> "outro";
		};
	}

	private static int frameIndexAt(List<StructureFeatureFrame> frames, double timeSeconds) {
		int best = 0;
		double bestDist = Double.MAX_VALUE;
		for (int i = 0; i < frames.size(); i++) {
			double dist = Math.abs(frames.get(i).timeSeconds() - timeSeconds);
			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	private static SectionStats statsFor(List<StructureFeatureFrame> frames, double start, double end) {
		float energySum = 0;
		float onsetSum = 0;
		float fluxSum = 0;
		int count = 0;
		float firstEnergy = -1;
		float lastEnergy = 0;
		for (StructureFeatureFrame frame : frames) {
			if (frame.timeSeconds() < start || frame.timeSeconds() > end) continue;
			if (firstEnergy < 0) firstEnergy = frame.energy();
			lastEnergy = frame.energy();
			energySum += frame.energy();
			onsetSum += frame.onsetDensity();
			fluxSum += frame.spectralFlux();
			count++;
		}
		if (count == 0) return new SectionStats(0, 0, 0);
		float avgEnergy = energySum / count;
		float avgOnset = onsetSum / count;
		float trend = count > 1 ? (lastEnergy - firstEnergy) / count : 0;
		return new SectionStats(avgEnergy, avgOnset, trend);
	}

	private static float[] meanVector(List<StructureFeatureFrame> frames, int startIndex, int endIndex) {
		float[] sum = new float[6];
		int count = 0;
		for (int i = Math.max(0, startIndex); i < Math.min(frames.size(), endIndex); i++) {
			float[] vector = frames.get(i).toVector();
			for (int d = 0; d < sum.length; d++) sum[d] += vector[d];
			count++;
		}
		if (count == 0) return sum;
		for (int d = 0; d < sum.length; d++) sum[d] /= count;
		return sum;
	}

	private record SectionStats(float energy, float onset, float trend) {}
}
