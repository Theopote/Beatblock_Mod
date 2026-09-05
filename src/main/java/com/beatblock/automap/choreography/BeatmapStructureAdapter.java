package com.beatblock.automap.choreography;

import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.structure.BarGridBuilder;
import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.MusicSection;
import com.beatblock.audio.beatmap.SectionLabel;
import com.beatblock.automap.engine.MusicStructureAnalyzer;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 将 Python {@link Beatmap} 段落与小节索引桥接为 {@link MusicStructure}，供编舞计划种子数据使用。
 * <p>
 * Phrase：优先使用 {@link MusicStructureAnalyzer}（有 FeatureTimeline 时）；否则按小节网格
 * （默认每 4 小节一段，并贴合 Section 边界）推导，避免空 Phrase 破坏 Phrase Grammar。
 */
public final class BeatmapStructureAdapter {

	/** 无 novelty 特征时，按常见流行乐句长：4 小节 = 1 Phrase。 */
	static final int BARS_PER_PHRASE = 4;
	private static final double DEFAULT_PHRASE_REPETITION = 0.45;

	private BeatmapStructureAdapter() {}

	public static MusicStructure fromBeatmap(Beatmap beatmap) {
		return fromBeatmap(beatmap, null);
	}

	public static MusicStructure fromBeatmap(Beatmap beatmap, @Nullable AudioFeatureTimeline features) {
		if (beatmap == null) {
			return MusicStructure.fallback(0);
		}
		double duration = Math.max(0.0, beatmap.meta.durationMs() / 1000.0);
		List<StructuralSection> sections = toStructuralSections(beatmap.sections, duration);
		List<BarGridBuilder.BarSpan> bars = buildBarsFromBeats(beatmap, duration);
		List<Double> beatTimes = beatmap.beats.stream()
			.mapToDouble(e -> e.timeMs() / 1000.0)
			.distinct()
			.sorted()
			.boxed()
			.toList();
		List<MusicStructure.PhraseSpan> phrases = resolvePhrases(features, bars, sections, duration);
		return new MusicStructure(duration, beatTimes, bars, phrases, sections);
	}

	private static List<MusicStructure.PhraseSpan> resolvePhrases(
		@Nullable AudioFeatureTimeline features,
		List<BarGridBuilder.BarSpan> bars,
		List<StructuralSection> sections,
		double durationSeconds
	) {
		if (features != null && features.getDurationSeconds() > 0) {
			List<MusicStructure.PhraseSpan> analyzed = MusicStructureAnalyzer.analyze(features).phrases();
			if (!analyzed.isEmpty()) {
				return analyzed;
			}
		}
		return buildPhrasesFromBars(bars, sections, durationSeconds);
	}

	/**
	 * 按小节网格推导 Phrase：优先在每个 Section 内按 {@link #BARS_PER_PHRASE} 切分；
	 * 无小节时退化为整段 Section / 全曲一条 Phrase。
	 */
	static List<MusicStructure.PhraseSpan> buildPhrasesFromBars(
		List<BarGridBuilder.BarSpan> bars,
		List<StructuralSection> sections,
		double durationSeconds
	) {
		if (durationSeconds <= 0) {
			return List.of();
		}
		List<StructuralSection> scope = (sections == null || sections.isEmpty())
			? List.of(new StructuralSection(0, durationSeconds, SectionType.VERSE))
			: sections;

		List<MusicStructure.PhraseSpan> phrases = new ArrayList<>();
		for (StructuralSection section : scope) {
			List<BarGridBuilder.BarSpan> inSection = barsInSection(bars, section);
			if (inSection.isEmpty()) {
				phrases.add(new MusicStructure.PhraseSpan(
					section.getStartSeconds(),
					section.getEndSeconds(),
					phrases.size(),
					DEFAULT_PHRASE_REPETITION
				));
				continue;
			}
			for (int i = 0; i < inSection.size(); i += BARS_PER_PHRASE) {
				int endBar = Math.min(inSection.size() - 1, i + BARS_PER_PHRASE - 1);
				double start = Math.max(section.getStartSeconds(), inSection.get(i).startSeconds());
				double end = Math.min(section.getEndSeconds(), inSection.get(endBar).endSeconds());
				if (endBar == inSection.size() - 1) {
					end = section.getEndSeconds();
				}
				if (end <= start) continue;
				phrases.add(new MusicStructure.PhraseSpan(start, end, phrases.size(), DEFAULT_PHRASE_REPETITION));
			}
		}
		if (phrases.isEmpty()) {
			phrases.add(new MusicStructure.PhraseSpan(0, durationSeconds, 0, DEFAULT_PHRASE_REPETITION));
		}
		return phrases;
	}

	private static List<BarGridBuilder.BarSpan> barsInSection(
		List<BarGridBuilder.BarSpan> bars,
		StructuralSection section
	) {
		if (bars == null || bars.isEmpty()) {
			return List.of();
		}
		List<BarGridBuilder.BarSpan> out = new ArrayList<>();
		for (BarGridBuilder.BarSpan bar : bars) {
			double mid = (bar.startSeconds() + bar.endSeconds()) * 0.5;
			if (mid >= section.getStartSeconds() && mid < section.getEndSeconds()) {
				out.add(bar);
			}
		}
		return out;
	}

	private static List<StructuralSection> toStructuralSections(List<MusicSection> sections, double durationSeconds) {
		if (sections == null || sections.isEmpty()) {
			return List.of(new StructuralSection(0, durationSeconds, SectionType.VERSE));
		}
		List<StructuralSection> out = new ArrayList<>(sections.size());
		for (MusicSection section : sections) {
			double start = Math.max(0.0, section.startMs() / 1000.0);
			double end = Math.min(durationSeconds, section.endMs() / 1000.0);
			if (end <= start) continue;
			SectionType type = mapSectionLabel(section.label());
			String label = section.label().name().toLowerCase();
			double confidence = section.energyMean() > 0 ? Math.max(0.35, Math.min(1.0, section.energyMean())) : 0.75;
			out.add(new StructuralSection(start, end, type, label, confidence));
		}
		if (out.isEmpty()) {
			out.add(new StructuralSection(0, durationSeconds, SectionType.VERSE));
		}
		return out;
	}

	private static SectionType mapSectionLabel(SectionLabel label) {
		if (label == null) return SectionType.VERSE;
		return switch (label) {
			case INTRO -> SectionType.INTRO;
			case VERSE -> SectionType.VERSE;
			case CHORUS -> SectionType.CHORUS;
			case BRIDGE -> SectionType.BRIDGE;
			case OUTRO -> SectionType.OUTRO;
			case UNKNOWN -> SectionType.VERSE;
		};
	}

	private static List<BarGridBuilder.BarSpan> buildBarsFromBeats(Beatmap beatmap, double durationSeconds) {
		if (beatmap.beats.isEmpty()) {
			return List.of(new BarGridBuilder.BarSpan(0, durationSeconds, 0));
		}
		double bpm = beatmap.meta.bpm();
		double barDuration = bpm > 0 ? (60.0 / bpm) * 4.0 : 2.0;

		TreeMap<Integer, Double> barStarts = new TreeMap<>();
		for (BeatEvent beat : beatmap.beats) {
			double timeSeconds = beat.timeMs() / 1000.0;
			barStarts.merge(beat.barIndex(), timeSeconds, Math::min);
		}

		List<BarGridBuilder.BarSpan> bars = new ArrayList<>(barStarts.size());
		List<Integer> indices = new ArrayList<>(barStarts.keySet());
		for (int i = 0; i < indices.size(); i++) {
			int barIndex = indices.get(i);
			double start = barStarts.get(barIndex);
			double end = i + 1 < indices.size()
				? barStarts.get(indices.get(i + 1))
				: Math.min(durationSeconds, start + barDuration);
			end = Math.max(end, Math.min(durationSeconds, start + barDuration * 0.5));
			bars.add(new BarGridBuilder.BarSpan(start, end, barIndex));
		}
		return bars;
	}
}
