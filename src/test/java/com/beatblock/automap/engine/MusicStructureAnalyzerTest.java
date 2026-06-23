package com.beatblock.automap.engine;

import com.beatblock.audio.analysis.EnergyFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicStructureAnalyzerTest {

	@Test
	void emptyEnergyFramesYieldSingleVerseSection() {
		List<MusicSection> sections = MusicStructureAnalyzer.analyze(List.of(), 60.0);

		assertEquals(1, sections.size());
		assertEquals(SectionType.VERSE, sections.getFirst().getType());
		assertEquals(0.0, sections.getFirst().getStartSeconds(), 1e-6);
		assertEquals(60.0, sections.getFirst().getEndSeconds(), 1e-6);
	}

	@Test
	void startSegmentIsIntroAndEndSegmentIsOutro() {
		List<EnergyFrame> frames = new java.util.ArrayList<>();
		for (int i = 0; i <= 25; i++) {
			frames.add(new EnergyFrame(i * 4.0, 0.1f));
		}

		List<MusicSection> sections = MusicStructureAnalyzer.analyze(frames, 100.0);

		assertEquals(SectionType.INTRO, sections.getFirst().getType());
		assertEquals(SectionType.OUTRO, sections.getLast().getType());
	}

	@Test
	void highEnergySegmentCanBecomeDrop() {
		List<EnergyFrame> frames = List.of(
			new EnergyFrame(12.0, 0.2f),
			new EnergyFrame(14.0, 0.95f),
			new EnergyFrame(16.0, 0.9f),
			new EnergyFrame(18.0, 0.85f)
		);

		List<MusicSection> sections = MusicStructureAnalyzer.analyze(frames, 24.0);

		assertTrue(sections.stream().anyMatch(s -> s.getType() == SectionType.DROP));
	}
}
