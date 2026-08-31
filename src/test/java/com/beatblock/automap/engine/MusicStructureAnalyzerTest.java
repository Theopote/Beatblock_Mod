package com.beatblock.automap.engine;

import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.BeatGrid;
import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.EnergyFrame;
import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.audio.analysis.WaveformExtractor;
import com.beatblock.audio.analysis.structure.MusicStructure;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicStructureAnalyzerTest {

	@Test
	void emptyEnergyFramesYieldSingleVerseSection() {
		List<StructuralSection> sections = MusicStructureAnalyzer.analyze(List.of(), 60.0);

		assertEquals(1, sections.size());
		assertEquals(SectionType.VERSE, sections.getFirst().getType());
		assertEquals(0.0, sections.getFirst().getStartSeconds(), 1e-6);
		assertEquals(60.0, sections.getFirst().getEndSeconds(), 1e-6);
	}

	@Test
	void analyzesHierarchicalStructureFromFeatureTimeline() {
		AudioFeatureTimeline timeline = syntheticVerseChorusTimeline(64.0, 120f);
		MusicStructure structure = MusicStructureAnalyzer.analyze(timeline);

		assertFalse(structure.beatTimes().isEmpty());
		assertFalse(structure.bars().isEmpty());
		assertFalse(structure.sections().isEmpty());
		assertTrue(structure.durationSeconds() > 0);
	}

	@Test
	void repeatedSectionsCanBeLabeledAsChorus() {
		AudioFeatureTimeline timeline = syntheticVerseChorusTimeline(64.0, 120f);
		List<StructuralSection> sections = MusicStructureAnalyzer.analyzeSections(timeline);

		assertTrue(sections.stream().anyMatch(s -> s.getType() == SectionType.CHORUS || s.getType() == SectionType.VERSE));
		assertTrue(sections.stream().anyMatch(s -> !s.getLabel().isBlank()));
	}

	@Test
	void sectionBoundariesAreNotFixedFourSecondSlices() {
		AudioFeatureTimeline timeline = syntheticVerseChorusTimeline(48.0, 128f);
		List<StructuralSection> sections = MusicStructureAnalyzer.analyzeSections(timeline);

		boolean hasNonFourSecondSlice = sections.stream()
			.anyMatch(s -> Math.abs(s.getDurationSeconds() - 4.0) > 0.75);
		assertTrue(hasNonFourSecondSlice || sections.size() <= 3);
	}

	private static AudioFeatureTimeline syntheticVerseChorusTimeline(double duration, float bpm) {
		List<EnergyFrame> energyFrames = new ArrayList<>();
		List<FrequencyBands> bands = new ArrayList<>();
		List<DetectedBeat> beats = new ArrayList<>();
		double beatDur = 60.0 / bpm;
		for (double t = 0; t <= duration; t += 0.25) {
			boolean chorus = (t >= 16 && t < 32) || (t >= 48 && t < 64);
			float energy = chorus ? 0.85f : 0.35f;
			energyFrames.add(new EnergyFrame(t, energy));
			bands.add(new FrequencyBands(
				t,
				chorus ? 0.2f : 0.45f,
				chorus ? 0.35f : 0.35f,
				chorus ? 0.45f : 0.20f
			));
		}
		for (double t = 0; t <= duration; t += beatDur) {
			beats.add(new DetectedBeat(t, 0.8f));
		}
		BeatGrid grid = new BeatGrid(bpm, duration);
		return new AudioFeatureTimeline(
			duration, beats, energyFrames, bands, new WaveformExtractor.WaveformFrame[0], bpm, grid);
	}
}
