package com.beatblock.automap.performance;

import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.automap.engine.PatternGenerator;
import com.beatblock.automap.engine.RhythmEvent;
import com.beatblock.automap.engine.RhythmType;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionActivityPatternFilterTest {

	@Test
	void sectionActivityAnchorsMatchBreathingTable() {
		assertEquals(0.25, SectionActivity.forSectionType(SectionType.INTRO), 1e-9);
		assertEquals(0.55, SectionActivity.forSectionType(SectionType.VERSE), 1e-9);
		assertEquals(0.80, SectionActivity.forSectionType(SectionType.BUILD), 1e-9);
		assertEquals(1.00, SectionActivity.forSectionType(SectionType.DROP), 1e-9);
		assertEquals(0.20, SectionActivity.forSectionType(SectionType.OUTRO), 1e-9);
	}

	@Test
	void phraseActivityDropsWithRepetition() {
		assertEquals(1.0, SectionActivity.phraseActivity(0.0), 1e-9);
		assertEquals(0.65, SectionActivity.phraseActivity(1.0), 1e-9);
	}

	@Test
	void filterWithoutStructureStaysUniform() {
		List<RhythmEvent> events = denseKicks(0, 16, 0.25, 0.9f);
		List<RhythmEvent> filtered = PatternGenerator.filter(events, Complexity.MEDIUM);

		long firstHalf = filtered.stream().filter(e -> e.getTimeSeconds() < 8).count();
		long secondHalf = filtered.stream().filter(e -> e.getTimeSeconds() >= 8).count();
		assertEquals(firstHalf, secondHalf);
	}

	@Test
	void filterWithStructureGivesDropMoreAccentsThanIntroAndOutro() {
		MusicStructure structure = structure(
			section(0, 4, SectionType.INTRO),
			section(4, 8, SectionType.VERSE),
			section(8, 12, SectionType.DROP),
			section(12, 16, SectionType.OUTRO)
		);
		List<RhythmEvent> events = denseKicks(0, 16, 0.05, 0.9f);

		List<RhythmEvent> filtered = PatternGenerator.filter(events, Complexity.MEDIUM, structure);
		Map<SectionType, Long> counts = countBySection(filtered, structure);

		long intro = counts.getOrDefault(SectionType.INTRO, 0L);
		long verse = counts.getOrDefault(SectionType.VERSE, 0L);
		long drop = counts.getOrDefault(SectionType.DROP, 0L);
		long outro = counts.getOrDefault(SectionType.OUTRO, 0L);

		assertTrue(drop > 0, "DROP should keep accents");
		assertTrue(intro <= drop * 0.40, "INTRO denser ratio vs DROP should be low, intro=" + intro + " drop=" + drop);
		assertTrue(outro <= drop * 0.35, "OUTRO should be sparsest, outro=" + outro + " drop=" + drop);
		assertTrue(verse < drop, "VERSE should be below DROP, verse=" + verse + " drop=" + drop);
		assertTrue(verse > intro, "VERSE should exceed INTRO density");
	}

	@Test
	void repeatedPhraseIsSparserThanNovelPhraseInSameSection() {
		MusicStructure structure = new MusicStructure(
			16,
			List.of(),
			List.of(),
			List.of(
				new MusicStructure.PhraseSpan(0, 4, 0, 0.0),
				new MusicStructure.PhraseSpan(4, 8, 1, 1.0)
			),
			List.of(new StructuralSection(0, 8, SectionType.VERSE))
		);
		List<RhythmEvent> events = denseKicks(0, 8, 0.05, 0.9f);

		List<RhythmEvent> filtered = PatternGenerator.filter(events, Complexity.MEDIUM, structure);
		long novel = filtered.stream().filter(e -> e.getTimeSeconds() < 4).count();
		long repeated = filtered.stream().filter(e -> e.getTimeSeconds() >= 4).count();

		assertTrue(novel >= repeated, "novel=" + novel + " repeated=" + repeated);
	}

	@Test
	void sparseSectionRaisesEnergyFloor() {
		MusicStructure structure = structure(
			section(0, 4, SectionType.INTRO),
			section(4, 8, SectionType.DROP)
		);
		List<RhythmEvent> events = List.of(
			new RhythmEvent(1.0, RhythmType.KICK, 0.35f),
			new RhythmEvent(5.0, RhythmType.KICK, 0.35f)
		);

		List<RhythmEvent> filtered = PatternGenerator.filter(events, Complexity.MEDIUM, structure);

		assertEquals(1, filtered.size());
		assertEquals(5.0, filtered.getFirst().getTimeSeconds(), 1e-6);
	}

	private static List<RhythmEvent> denseKicks(double start, double end, double step, float energy) {
		List<RhythmEvent> events = new ArrayList<>();
		for (double t = start; t < end - 1e-9; t += step) {
			events.add(new RhythmEvent(t, RhythmType.KICK, energy));
		}
		return events;
	}

	private static MusicStructure structure(StructuralSection... sections) {
		double end = sections[sections.length - 1].getEndSeconds();
		return new MusicStructure(end, List.of(), List.of(), List.of(), List.of(sections));
	}

	private static StructuralSection section(double start, double end, SectionType type) {
		return new StructuralSection(start, end, type);
	}

	private static Map<SectionType, Long> countBySection(List<RhythmEvent> events, MusicStructure structure) {
		Map<SectionType, Long> counts = new EnumMap<>(SectionType.class);
		for (RhythmEvent event : events) {
			StructuralSection section = SectionActivity.sectionAt(structure, event.getTimeSeconds());
			if (section != null) {
				counts.merge(section.getType(), 1L, Long::sum);
			}
		}
		return counts;
	}
}
