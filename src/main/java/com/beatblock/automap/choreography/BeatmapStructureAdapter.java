package com.beatblock.automap.choreography;

import com.beatblock.audio.analysis.structure.BarGridBuilder;
import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.MusicSection;
import com.beatblock.audio.beatmap.SectionLabel;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 将 Python {@link Beatmap} 段落与小节索引桥接为 {@link MusicStructure}，供编舞计划种子数据使用。
 */
public final class BeatmapStructureAdapter {

	private BeatmapStructureAdapter() {}

	public static MusicStructure fromBeatmap(Beatmap beatmap) {
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
		return new MusicStructure(duration, beatTimes, bars, List.of(), sections);
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
			out.add(new StructuralSection(start, end, type, label));
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
