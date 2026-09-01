package com.beatblock.automap.choreography;

import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.BeatGrid;
import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.EnergyFrame;
import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.audio.analysis.WaveformExtractor;
import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.MusicStructureAnalyzer;
import com.beatblock.automap.engine.RhythmEvent;
import com.beatblock.automap.engine.RhythmType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographyPlanBuilderMusicStructureTest {

	@Test
	void fromMusicStructurePopulatesBarsPhrasesAndRepeats() {
		AudioFeatureTimeline timeline = syntheticVerseChorusTimeline(64.0, 120f);
		MusicStructure structure = MusicStructureAnalyzer.analyze(timeline);
		List<RhythmEvent> rhythms = List.of(new RhythmEvent(1.0, RhythmType.KICK, 0.8f));

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromMusicStructure(
			structure,
			rhythms,
			List.of(),
			List.of(),
			AutoMapStyle.EDM,
			AutoMapConfig.createDefault()
		);

		assertFalse(plan.sections().isEmpty());
		assertFalse(plan.musicalStructure().bars().isEmpty());
		assertFalse(plan.musicalStructure().isEmpty());
		assertTrue(plan.barIndexAt(
			plan.musicalStructure().bars().getFirst().startSeconds() + 0.01) >= 0);
		if (!plan.musicalStructure().phrases().isEmpty()) {
			var phrase = plan.musicalStructure().phrases().getFirst();
			assertEquals(phrase.phraseIndex(), plan.musicalPhraseIndexAt(
				(phrase.startSeconds() + phrase.endSeconds()) * 0.5));
		}
	}

	@Test
	void phraseNoveltyBoostsDensityCurve() {
		AudioFeatureTimeline timeline = syntheticVerseChorusTimeline(64.0, 120f);
		MusicStructure structure = MusicStructureAnalyzer.analyze(timeline);

		ChoreographyPlan withStructure = ChoreographyPlanBuilder.fromMusicStructure(
			structure, List.of(), List.of(), List.of(), AutoMapStyle.EDM, AutoMapConfig.createDefault());
		ChoreographyPlan withoutStructure = ChoreographyPlanBuilder.fromRhythmAnalysis(
			List.of(), structure.sections(), List.of(), List.of(), AutoMapStyle.EDM, AutoMapConfig.createDefault());

		if (!withStructure.musicalStructure().phrases().isEmpty()) {
			var phrase = withStructure.musicalStructure().phrases().getFirst();
			double mid = (phrase.startSeconds() + phrase.endSeconds()) * 0.5;
			assertTrue(withStructure.densityCurve().sampleAt(mid)
				>= withoutStructure.densityCurve().sampleAt(mid));
		}
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
