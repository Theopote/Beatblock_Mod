package com.beatblock.audio.analysis.structure;

import com.beatblock.automap.engine.StructuralSection;
import com.beatblock.audio.analysis.BeatGrid;
import com.beatblock.audio.analysis.DetectedBeat;

import java.util.ArrayList;
import java.util.List;

/**
 * 三级+结构分析结果：Beat → Bar → Phrase → Section。
 */
public final class MusicStructure {

	public record PhraseSpan(double startSeconds, double endSeconds, int phraseIndex, double repetitionScore) {}

	private final double durationSeconds;
	private final List<Double> beatTimes;
	private final List<BarGridBuilder.BarSpan> bars;
	private final List<PhraseSpan> phrases;
	private final List<StructuralSection> sections;

	public MusicStructure(
		double durationSeconds,
		List<Double> beatTimes,
		List<BarGridBuilder.BarSpan> bars,
		List<PhraseSpan> phrases,
		List<StructuralSection> sections
	) {
		this.durationSeconds = Math.max(0, durationSeconds);
		this.beatTimes = beatTimes != null ? List.copyOf(beatTimes) : List.of();
		this.bars = bars != null ? List.copyOf(bars) : List.of();
		this.phrases = phrases != null ? List.copyOf(phrases) : List.of();
		this.sections = sections != null ? List.copyOf(sections) : List.of();
	}

	public static MusicStructure fallback(double durationSeconds) {
		return new MusicStructure(
			durationSeconds,
			List.of(),
			List.of(new BarGridBuilder.BarSpan(0, durationSeconds, 0)),
			List.of(),
			List.of(new StructuralSection(0, durationSeconds, com.beatblock.automap.engine.SectionType.VERSE))
		);
	}

	public double durationSeconds() { return durationSeconds; }
	public List<Double> beatTimes() { return beatTimes; }
	public List<BarGridBuilder.BarSpan> bars() { return bars; }
	public List<PhraseSpan> phrases() { return phrases; }
	public List<StructuralSection> sections() { return sections; }

	public static List<PhraseSpan> buildPhrases(
		List<Double> phraseBoundaries,
		List<StructureFeatureFrame> frames,
		double[][] similarity
	) {
		if (phraseBoundaries == null || phraseBoundaries.size() < 2) return List.of();
		List<PhraseSpan> phrases = new ArrayList<>();
		for (int i = 0; i < phraseBoundaries.size() - 1; i++) {
			double start = phraseBoundaries.get(i);
			double end = phraseBoundaries.get(i + 1);
			int startIndex = frameIndexAt(frames, start);
			int endIndex = frameIndexAt(frames, end);
			double repetition = SelfSimilarityMatrix.maxPriorSimilarity(similarity, startIndex, endIndex);
			phrases.add(new PhraseSpan(start, end, i, repetition));
		}
		return phrases;
	}

	public static List<Double> collectBeatTimes(BeatGrid grid, List<DetectedBeat> beats, double durationSeconds) {
		if (beats != null && !beats.isEmpty()) {
			return beats.stream().map(DetectedBeat::getTimeSeconds).toList();
		}
		if (grid != null) return grid.getBeatTimes();
		return List.of();
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
}
