package com.beatblock.automap.choreography;

import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.audio.beatmap.AnchorType;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapMeta;
import com.beatblock.audio.beatmap.MusicSection;
import com.beatblock.audio.beatmap.SectionLabel;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.IntensityEnvelope;
import com.beatblock.automap.choreography.grammar.MotionPresetSpec;
import com.beatblock.automap.choreography.grammar.SpatialPatternSpec;
import com.beatblock.automap.choreography.grammar.TargetSet;
import com.beatblock.automap.choreography.grammar.TimingPatternSpec;
import com.beatblock.automap.choreography.grammar.TriggerSpec;
import com.beatblock.automap.choreography.grammar.VariationSpec;
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

	@Test
	void seedPreservesLockedSectionAgainstReanalyzedBoundaries() {
		Timeline timeline = Timeline.createDefault();
		ChoreographyPlan existing = planWithMusic(
			List.of(section(0, 20, SectionType.VERSE, 0.95, SectionPlanSource.LOCKED)),
			new ChoreographyPlan.MusicalStructure(
				List.of(
					new ChoreographyPlan.BarPlan(0, 4, 0, 0),
					new ChoreographyPlan.BarPlan(8, 12, 1, 0)
				),
				List.of(
					musicalPhrase(0, 10, 0, 0, 0.8),
					musicalPhrase(10, 20, 1, 0, 0.75)
				),
				List.of(),
				List.of(0.0, 1.0, 2.0)
			)
		);
		ChoreographyPlanStore.save(timeline, existing, null);

		// Reanalysis splits 0–20 into INTRO + CHORUS.
		ChoreographyPlanSeeder.seedFromBeatmap(timeline, beatmapWithSections(
			new MusicSection(0, 10_000, SectionLabel.INTRO, 0.4f),
			new MusicSection(10_000, 20_000, SectionLabel.CHORUS, 0.9f)
		));

		ChoreographyPlan merged = ChoreographyPlanStore.loadPlan(timeline);
		assertNotNull(merged);
		assertEquals(1, merged.sections().size());
		ChoreographyPlan.SectionPlan locked = merged.sections().getFirst();
		assertEquals(SectionPlanSource.LOCKED, locked.source());
		assertEquals(SectionType.VERSE, locked.sectionType());
		assertEquals(0.0, locked.startSeconds());
		assertEquals(20.0, locked.endSeconds());

		assertMusicalStructureAlignedWithSections(merged);
	}

	@Test
	void seedPreservesUserEditedSectionAgainstReanalyzedBoundaries() {
		Timeline timeline = Timeline.createDefault();
		ChoreographyPlan existing = planWithMusic(
			List.of(section(0, 20, SectionType.VERSE, 0.8, SectionPlanSource.USER_EDITED)),
			new ChoreographyPlan.MusicalStructure(
				List.of(new ChoreographyPlan.BarPlan(0, 4, 0, 0)),
				List.of(musicalPhrase(0, 20, 0, 0, 0.7)),
				List.of(),
				List.of(0.0, 1.0)
			)
		);
		ChoreographyPlanStore.save(timeline, existing, null);

		ChoreographyPlanSeeder.seedFromBeatmap(timeline, beatmapWithSections(
			new MusicSection(0, 10_000, SectionLabel.INTRO, 0.4f),
			new MusicSection(10_000, 20_000, SectionLabel.CHORUS, 0.9f)
		));

		ChoreographyPlan merged = ChoreographyPlanStore.loadPlan(timeline);
		assertNotNull(merged);
		assertEquals(1, merged.sections().size());
		assertEquals(SectionPlanSource.USER_EDITED, merged.sections().getFirst().source());
		assertEquals(SectionType.VERSE, merged.sections().getFirst().sectionType());
		assertEquals(0.0, merged.sections().getFirst().startSeconds());
		assertEquals(20.0, merged.sections().getFirst().endSeconds());
		assertMusicalStructureAlignedWithSections(merged);
	}

	@Test
	void mergeStructureOnlyKeepsLockedPhraseSpansOverFinerReanalysis() {
		ChoreographyPlan existing = planWithMusic(
			List.of(section(0, 16, SectionType.VERSE, 0.95, SectionPlanSource.LOCKED)),
			new ChoreographyPlan.MusicalStructure(
				List.of(new ChoreographyPlan.BarPlan(0, 4, 0, 0)),
				List.of(
					musicalPhrase(0, 8, 0, 0, 0.9),
					musicalPhrase(8, 16, 1, 0, 0.85)
				),
				List.of(),
				List.of(0.0, 1.0)
			)
		);
		ChoreographyPlan analyzed = planWithMusic(
			List.of(
				section(0, 8, SectionType.INTRO, 0.7, SectionPlanSource.ANALYZED),
				section(8, 16, SectionType.CHORUS, 0.8, SectionPlanSource.ANALYZED)
			),
			new ChoreographyPlan.MusicalStructure(
				List.of(
					new ChoreographyPlan.BarPlan(0, 4, 0, 0),
					new ChoreographyPlan.BarPlan(8, 12, 1, 1)
				),
				List.of(
					musicalPhrase(0, 4, 0, 0, 0.4),
					musicalPhrase(4, 8, 1, 0, 0.45),
					musicalPhrase(8, 12, 2, 1, 0.5),
					musicalPhrase(12, 16, 3, 1, 0.55)
				),
				List.of(),
				List.of(0.0, 0.5, 1.0, 1.5)
			)
		);

		ChoreographyPlan merged = ChoreographyPlanSeeder.mergeStructure(existing, analyzed);

		assertEquals(1, merged.sections().size());
		assertEquals(SectionPlanSource.LOCKED, merged.sections().getFirst().source());
		assertEquals(List.of(0.0, 0.5, 1.0, 1.5), merged.musicalStructure().beatTimes());

		assertEquals(2, merged.musicalStructure().phrases().size());
		assertTrue(merged.musicalStructure().phrases().stream().anyMatch(p ->
			p.startSeconds() == 0.0 && p.endSeconds() == 8.0 && p.repetitionScore() == 0.9));
		assertTrue(merged.musicalStructure().phrases().stream().anyMatch(p ->
			p.startSeconds() == 8.0 && p.endSeconds() == 16.0 && p.repetitionScore() == 0.85));
		assertFalse(merged.musicalStructure().phrases().stream().anyMatch(p ->
			p.endSeconds() - p.startSeconds() == 4.0));

		assertMusicalStructureAlignedWithSections(merged);
	}

	@Test
	void fullMergeSuppressesOnlyGrammarPhrasesOverlappingProtectedAtThirtySeconds() {
		// Protected begins at 30s — must not use fake t=0 for all grammar phrases.
		ChoreographyPlan existing = planWithGrammar(
			List.of(
				section(0, 30, SectionType.VERSE, 0.6, SectionPlanSource.ANALYZED),
				section(30, 50, SectionType.CHORUS, 0.95, SectionPlanSource.LOCKED)
			),
			List.of(
				grammarPhrase("keep-locked", 1),
				grammarPhrase("drop-old-verse", 0)
			)
		);
		ChoreographyPlan analyzed = planWithGrammar(
			List.of(
				section(0, 15, SectionType.INTRO, 0.7, SectionPlanSource.ANALYZED),
				section(15, 30, SectionType.BUILD, 0.7, SectionPlanSource.ANALYZED),
				section(30, 50, SectionType.DROP, 0.8, SectionPlanSource.ANALYZED)
			),
			List.of(
				grammarPhrase("section0", 0),
				grammarPhrase("section1", 1),
				grammarPhrase("section2", 2)
			)
		);

		ChoreographyPlan merged = ChoreographyStructureMerger.merge(existing, analyzed);

		assertTrue(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("keep-locked")));
		assertTrue(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("section0")));
		assertTrue(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("section1")));
		assertFalse(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("section2")));
		assertFalse(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("drop-old-verse")));
	}

	private static void assertMusicalStructureAlignedWithSections(ChoreographyPlan plan) {
		for (ChoreographyPlan.BarPlan bar : plan.musicalStructure().bars()) {
			double mid = (bar.startSeconds() + bar.endSeconds()) * 0.5;
			assertEquals(
				MusicalStructureMapper.resolveSectionIndex(plan.sections(), mid),
				bar.sectionIndex(),
				"bar sectionIndex mismatch @" + bar.startSeconds()
			);
		}
		for (ChoreographyPlan.MusicalPhrasePlan phrase : plan.musicalStructure().phrases()) {
			double mid = (phrase.startSeconds() + phrase.endSeconds()) * 0.5;
			assertEquals(
				MusicalStructureMapper.resolveSectionIndex(plan.sections(), mid),
				phrase.sectionIndex(),
				"phrase sectionIndex mismatch @" + phrase.startSeconds()
			);
		}
	}

	private static Beatmap sampleBeatmap() {
		return beatmapWithSections(
			new MusicSection(0, 8000, SectionLabel.INTRO, 0.3f),
			new MusicSection(8000, 16000, SectionLabel.CHORUS, 0.8f)
		);
	}

	private static Beatmap beatmapWithSections(MusicSection... sections) {
		long durationMs = 0;
		for (MusicSection section : sections) {
			durationMs = Math.max(durationMs, section.endMs());
		}
		return new Beatmap(
			1,
			new BeatmapMeta("song.wav", durationMs, 120, 1.0, "4/4", 44100, "", "", null, null, null),
			List.of(
				new BeatEvent(0, "kick", 0.8f, AnchorType.ARRIVE, 0, 0, 0),
				new BeatEvent(500, "snare", 0.7f, AnchorType.ARRIVE, 1, 0, 1),
				new BeatEvent(1000, "kick", 0.75f, AnchorType.ARRIVE, 2, 0, 2),
				new BeatEvent(1500, "snare", 0.7f, AnchorType.ARRIVE, 3, 0, 3)
			),
			List.of(sections),
			null,
			null
		);
	}

	private static ChoreographyPlan planWithMusic(
		List<ChoreographyPlan.SectionPlan> sections,
		ChoreographyPlan.MusicalStructure musicalStructure
	) {
		return new ChoreographyPlan(
			sections,
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			musicalStructure
		);
	}

	private static ChoreographyPlan planWithGrammar(
		List<ChoreographyPlan.SectionPlan> sections,
		List<ChoreographyPhrase> grammarPhrases
	) {
		return new ChoreographyPlan(
			sections,
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(),
			grammarPhrases
		);
	}

	private static ChoreographyPlan.SectionPlan section(
		double start,
		double end,
		SectionType type,
		double confidence,
		SectionPlanSource source
	) {
		return new ChoreographyPlan.SectionPlan(start, end, type, type.name().toLowerCase(), confidence, source);
	}

	private static ChoreographyPlan.MusicalPhrasePlan musicalPhrase(
		double start,
		double end,
		int phraseIndex,
		int sectionIndex,
		double repetitionScore
	) {
		return new ChoreographyPlan.MusicalPhrasePlan(
			start, end, phraseIndex, sectionIndex, repetitionScore, -1);
	}

	private static ChoreographyPhrase grammarPhrase(String targetId, int sectionIndex) {
		return new ChoreographyPhrase(
			new TriggerSpec.OnFeature("kick"),
			TargetSet.of(targetId),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			TimingPatternSpec.stagger(0.08),
			IntensityEnvelope.flat(0.8f),
			VariationSpec.none(),
			sectionIndex
		);
	}
}
