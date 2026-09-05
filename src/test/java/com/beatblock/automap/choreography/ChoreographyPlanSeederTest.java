package com.beatblock.automap.choreography;

import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.audio.beatmap.AnchorType;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapMeta;
import com.beatblock.audio.beatmap.MusicSection;
import com.beatblock.audio.beatmap.SectionLabel;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.Timeline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographyPlanSeederTest {

	@Test
	void seedFromBeatmapCreatesStructureOnlyPlan() {
		Timeline timeline = Timeline.createDefault();
		Beatmap beatmap = sampleBeatmap();

		ChoreographyPlanSeeder.seedFromBeatmap(timeline, beatmap);

		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		assertNotNull(plan);
		assertEquals(2, plan.sections().size());
		assertFalse(plan.musicalStructure().bars().isEmpty());
		assertFalse(plan.musicalStructure().phrases().isEmpty());
		assertTrue(plan.motionPhrases().isEmpty());
	}

	@Test
	void mergePreservesMotionPhrasesWhileRefreshingStructure() {
		Timeline timeline = Timeline.createDefault();
		ChoreographyPlan existing = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 8, SectionType.VERSE, "old")),
			List.of(),
			List.of(new ChoreographyPlan.MotionPhrase(1.0, "kick", "low", 0.8f, "bounce", 0.5, true, 4f, 0)),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.5)
		);
		ChoreographyPlanStore.save(timeline, existing, null);

		ChoreographyPlanSeeder.seedFromBeatmap(timeline, sampleBeatmap());

		ChoreographyPlan merged = ChoreographyPlanStore.loadPlan(timeline);
		assertNotNull(merged);
		assertEquals(2, merged.sections().size());
		assertEquals(1, merged.motionPhrases().size());
		assertFalse(merged.musicalStructure().bars().isEmpty());
	}

	@Test
	void fillTimelineFromBeatmapSeedsChoreographyPlan() {
		Timeline timeline = Timeline.createDefault();
		AudioAnalysisEngine engine = new AudioAnalysisEngine();
		engine.fillTimelineFromBeatmap(timeline, sampleBeatmap());

		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		assertNotNull(plan);
		assertEquals(SectionType.INTRO, plan.sections().get(0).sectionType());
	}

	private static Beatmap sampleBeatmap() {
		return new Beatmap(
			1,
			new BeatmapMeta("song.wav", 16000, 120, 1.0, "4/4", 44100, "", "", null, null, null),
			List.of(
				new BeatEvent(0, "kick", 0.8f, AnchorType.ARRIVE, 0, 0, 0),
				new BeatEvent(500, "snare", 0.7f, AnchorType.ARRIVE, 1, 0, 1)
			),
			List.of(
				new MusicSection(0, 8000, SectionLabel.INTRO, 0.3f),
				new MusicSection(8000, 16000, SectionLabel.CHORUS, 0.8f)
			),
			null,
			null
		);
	}
}
