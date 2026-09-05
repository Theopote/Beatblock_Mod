package com.beatblock.automap.choreography;

import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.IntensityEnvelope;
import com.beatblock.automap.choreography.grammar.MotionPresetSpec;
import com.beatblock.automap.choreography.grammar.SpatialPatternSpec;
import com.beatblock.automap.choreography.grammar.TargetSet;
import com.beatblock.automap.choreography.grammar.TimingPatternSpec;
import com.beatblock.automap.choreography.grammar.TriggerSpec;
import com.beatblock.automap.choreography.grammar.VariationSpec;
import com.beatblock.automap.engine.SectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographyStructureMergerTest {

	@Test
	void returnsAnalyzedWhenNoExistingPlan() {
		ChoreographyPlan analyzed = plan(
			section(0, 16, SectionType.INTRO, 0.8, SectionPlanSource.ANALYZED)
		);

		ChoreographyPlan merged = ChoreographyStructureMerger.merge(null, analyzed);

		assertEquals(1, merged.sections().size());
		assertEquals(SectionPlanSource.ANALYZED, merged.sections().getFirst().source());
	}

	@Test
	void replacesAllSectionsWhenNoneAreProtected() {
		ChoreographyPlan existing = plan(
			section(0, 8, SectionType.VERSE, 0.5, SectionPlanSource.ANALYZED)
		);
		ChoreographyPlan analyzed = plan(
			section(0, 12, SectionType.INTRO, 0.9, SectionPlanSource.ANALYZED),
			section(12, 24, SectionType.DROP, 0.85, SectionPlanSource.ANALYZED)
		);

		ChoreographyPlan merged = ChoreographyStructureMerger.merge(existing, analyzed);

		assertEquals(2, merged.sections().size());
		assertEquals(SectionType.INTRO, merged.sections().get(0).sectionType());
		assertEquals(SectionType.DROP, merged.sections().get(1).sectionType());
	}

	@Test
	void preservesLockedSectionAcrossReanalysis() {
		ChoreographyPlan existing = plan(
			section(0, 10, SectionType.VERSE, 0.6, SectionPlanSource.ANALYZED),
			section(10, 20, SectionType.CHORUS, 0.95, SectionPlanSource.LOCKED)
		);
		ChoreographyPlan analyzed = plan(
			section(0, 15, SectionType.INTRO, 0.7, SectionPlanSource.ANALYZED),
			section(15, 30, SectionType.DROP, 0.8, SectionPlanSource.ANALYZED)
		);

		ChoreographyPlan merged = ChoreographyStructureMerger.merge(existing, analyzed);

		assertTrue(merged.sections().stream().anyMatch(s ->
			s.source() == SectionPlanSource.LOCKED
				&& s.sectionType() == SectionType.CHORUS
				&& s.startSeconds() == 10.0
				&& s.endSeconds() == 20.0));
		assertTrue(merged.sections().stream().anyMatch(s ->
			s.source() == SectionPlanSource.ANALYZED && s.startSeconds() < 10.0));
	}

	@Test
	void preservesMotionPhrasesInLockedSection() {
		ChoreographyPlan existing = new ChoreographyPlan(
			List.of(
				section(0, 10, SectionType.VERSE, 0.6, SectionPlanSource.ANALYZED),
				section(10, 20, SectionType.CHORUS, 0.95, SectionPlanSource.LOCKED)
			),
			List.of(),
			List.of(
				new ChoreographyPlan.MotionPhrase(1.0, "verse", "low", 0.6f, "bounce", 0.5, true, 4f, 0),
				new ChoreographyPlan.MotionPhrase(11.0, "chorus", "low", 0.9f, "spin", 0.5, true, 4f, 1)
			),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		ChoreographyPlan analyzed = new ChoreographyPlan(
			List.of(
				section(0, 15, SectionType.INTRO, 0.7, SectionPlanSource.ANALYZED),
				section(15, 30, SectionType.DROP, 0.8, SectionPlanSource.ANALYZED)
			),
			List.of(),
			List.of(
				new ChoreographyPlan.MotionPhrase(1.0, "intro", "low", 0.7f, "slide", 0.5, true, 4f, 0),
				new ChoreographyPlan.MotionPhrase(16.0, "drop", "low", 0.9f, "pulse", 0.5, true, 4f, 1)
			),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);

		ChoreographyPlan merged = ChoreographyStructureMerger.merge(existing, analyzed);

		assertTrue(merged.motionPhrases().stream().anyMatch(p -> "spin".equals(p.animationTypeId())));
		assertTrue(merged.motionPhrases().stream().anyMatch(p -> "intro".equals(p.trackKey())));
		assertFalse(merged.motionPhrases().stream().anyMatch(p -> "drop".equals(p.trackKey())));
	}

	@Test
	void mergeGrammarPhrasesUsesSectionIndexNotFakeZeroTime() {
		// Locked chorus 10–20 does NOT contain t=0. Old bug (time=0) would keep ALL analyzed grammar phrases.
		ChoreographyPhrase lockedGrammar = grammarPhrase("locked-chorus", 1);
		ChoreographyPhrase analyzedIntro = grammarPhrase("analyzed-intro", 0);
		ChoreographyPhrase analyzedOverlap = grammarPhrase("analyzed-overlap", 1);

		ChoreographyPlan existing = planWithGrammar(
			List.of(
				section(0, 10, SectionType.VERSE, 0.6, SectionPlanSource.ANALYZED),
				section(10, 20, SectionType.CHORUS, 0.95, SectionPlanSource.LOCKED)
			),
			List.of(lockedGrammar, grammarPhrase("old-verse", 0))
		);
		ChoreographyPlan analyzed = planWithGrammar(
			List.of(
				// Adjacent to locked (no overlap): keep.
				section(0, 10, SectionType.INTRO, 0.7, SectionPlanSource.ANALYZED),
				// Overlaps locked 10–20: discard.
				section(15, 30, SectionType.DROP, 0.8, SectionPlanSource.ANALYZED)
			),
			List.of(analyzedIntro, analyzedOverlap)
		);

		ChoreographyPlan merged = ChoreographyStructureMerger.merge(existing, analyzed);

		assertTrue(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("locked-chorus")));
		assertTrue(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("analyzed-intro")));
		assertFalse(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("analyzed-overlap")));
		assertFalse(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("old-verse")));
	}

	@Test
	void mergeGrammarPhrasesDoesNotDropAllWhenProtectedIncludesZero() {
		// Locked intro 0–10. Old bug (time=0) would drop ALL analyzed grammar phrases.
		ChoreographyPhrase lockedIntro = grammarPhrase("locked-intro", 0);
		ChoreographyPhrase analyzedChorus = grammarPhrase("analyzed-chorus", 1);

		ChoreographyPlan existing = planWithGrammar(
			List.of(
				section(0, 10, SectionType.INTRO, 0.9, SectionPlanSource.LOCKED),
				section(10, 20, SectionType.VERSE, 0.5, SectionPlanSource.ANALYZED)
			),
			List.of(lockedIntro)
		);
		ChoreographyPlan analyzed = planWithGrammar(
			List.of(
				section(0, 8, SectionType.INTRO, 0.7, SectionPlanSource.ANALYZED),
				// Adjacent after locked: keep.
				section(10, 20, SectionType.CHORUS, 0.8, SectionPlanSource.ANALYZED)
			),
			List.of(grammarPhrase("analyzed-intro", 0), analyzedChorus)
		);

		ChoreographyPlan merged = ChoreographyStructureMerger.merge(existing, analyzed);

		assertTrue(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("locked-intro")));
		assertTrue(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("analyzed-chorus")));
		assertFalse(merged.choreographyPhrases().stream().anyMatch(p ->
			p.targets().objectIds().contains("analyzed-intro")));
	}

	@Test
	void mergeStructureOnlyKeepsExistingPhrases() {
		ChoreographyPlan existing = new ChoreographyPlan(
			List.of(section(0, 16, SectionType.VERSE, 0.5, SectionPlanSource.USER_EDITED)),
			List.of(),
			List.of(new ChoreographyPlan.MotionPhrase(1.0, "kick", "low", 0.8f, "bounce", 0.5, true, 4f, 0)),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.5)
		);
		ChoreographyPlan analyzed = plan(
			section(0, 16, SectionType.INTRO, 0.9, SectionPlanSource.ANALYZED)
		);

		ChoreographyPlan merged = ChoreographyStructureMerger.mergeStructureOnly(existing, analyzed);

		assertEquals(1, merged.motionPhrases().size());
		assertEquals(SectionType.VERSE, merged.sections().getFirst().sectionType());
		assertEquals(SectionPlanSource.USER_EDITED, merged.sections().getFirst().source());
	}

	@Test
	void mergeMusicalStructureKeepsProtectedPhrasesAndUsesAnalyzedBeatGrid() {
		// Locked verse 10–30; reanalysis splits that span into verse 10–24 + pre 24–30.
		ChoreographyPlan existing = planWithMusic(
			List.of(
				section(0, 10, SectionType.INTRO, 0.7, SectionPlanSource.ANALYZED),
				section(10, 30, SectionType.VERSE, 0.95, SectionPlanSource.LOCKED)
			),
			new ChoreographyPlan.MusicalStructure(
				List.of(
					new ChoreographyPlan.BarPlan(0, 4, 0, 0),
					new ChoreographyPlan.BarPlan(10, 14, 1, 1)
				),
				List.of(
					musicalPhrase(0, 8, 0, 0, 0.4),
					musicalPhrase(10, 18, 1, 1, 0.9),
					musicalPhrase(18, 26, 2, 1, 0.85)
				),
				List.of(),
				List.of(0.0, 0.5, 1.0)
			)
		);
		ChoreographyPlan analyzed = planWithMusic(
			List.of(
				section(0, 10, SectionType.INTRO, 0.8, SectionPlanSource.ANALYZED),
				section(10, 24, SectionType.VERSE, 0.7, SectionPlanSource.ANALYZED),
				section(24, 30, SectionType.BUILD, 0.6, SectionPlanSource.ANALYZED)
			),
			new ChoreographyPlan.MusicalStructure(
				List.of(
					new ChoreographyPlan.BarPlan(0, 4, 0, 0),
					new ChoreographyPlan.BarPlan(10, 14, 1, 1),
					new ChoreographyPlan.BarPlan(24, 28, 2, 2)
				),
				List.of(
					musicalPhrase(0, 8, 0, 0, 0.5),
					musicalPhrase(10, 16, 1, 1, 0.4),
					musicalPhrase(16, 24, 2, 1, 0.45),
					musicalPhrase(24, 30, 3, 2, 0.55)
				),
				List.of(),
				List.of(0.0, 0.25, 0.5, 0.75, 1.0)
			)
		);

		ChoreographyPlan merged = ChoreographyStructureMerger.mergeStructureOnly(existing, analyzed);

		assertTrue(merged.sections().stream().anyMatch(s ->
			s.source() == SectionPlanSource.LOCKED
				&& s.startSeconds() == 10.0
				&& s.endSeconds() == 30.0
				&& s.sectionType() == SectionType.VERSE));

		// Beat grid / bars come from reanalysis.
		assertEquals(List.of(0.0, 0.25, 0.5, 0.75, 1.0), merged.musicalStructure().beatTimes());
		assertEquals(3, merged.musicalStructure().bars().size());

		// Protected-span phrases kept; analyzed phrases inside 10–30 dropped.
		assertTrue(merged.musicalStructure().phrases().stream().anyMatch(p ->
			p.startSeconds() == 10.0 && p.endSeconds() == 18.0 && p.repetitionScore() == 0.9));
		assertTrue(merged.musicalStructure().phrases().stream().anyMatch(p ->
			p.startSeconds() == 18.0 && p.endSeconds() == 26.0 && p.repetitionScore() == 0.85));
		assertFalse(merged.musicalStructure().phrases().stream().anyMatch(p ->
			p.startSeconds() == 10.0 && p.endSeconds() == 16.0));
		assertFalse(merged.musicalStructure().phrases().stream().anyMatch(p ->
			p.startSeconds() == 24.0 && p.endSeconds() == 30.0));

		// Outside protected: new intro phrase kept.
		assertTrue(merged.musicalStructure().phrases().stream().anyMatch(p ->
			p.startSeconds() == 0.0 && p.endSeconds() == 8.0 && p.repetitionScore() == 0.5));

		assertMusicalPhraseSectionIndicesMatchPlanSections(merged);
		assertBarSectionIndicesMatchPlanSections(merged);
	}

	@Test
	void mergeMusicalStructureRebindsSectionIndexWithoutProtectedSections() {
		ChoreographyPlan existing = planWithMusic(
			List.of(section(0, 16, SectionType.VERSE, 0.5, SectionPlanSource.ANALYZED)),
			new ChoreographyPlan.MusicalStructure(
				List.of(new ChoreographyPlan.BarPlan(0, 4, 0, 0)),
				List.of(musicalPhrase(0, 8, 0, 0, 0.3)),
				List.of(),
				List.of(0.0, 1.0)
			)
		);
		ChoreographyPlan analyzed = planWithMusic(
			List.of(
				section(0, 8, SectionType.INTRO, 0.9, SectionPlanSource.ANALYZED),
				section(8, 16, SectionType.CHORUS, 0.85, SectionPlanSource.ANALYZED)
			),
			new ChoreographyPlan.MusicalStructure(
				List.of(
					new ChoreographyPlan.BarPlan(0, 4, 0, 0),
					new ChoreographyPlan.BarPlan(8, 12, 1, 1)
				),
				List.of(
					musicalPhrase(0, 8, 0, 0, 0.4),
					musicalPhrase(8, 16, 1, 1, 0.7)
				),
				List.of(),
				List.of(0.0, 0.5, 1.0)
			)
		);

		ChoreographyPlan merged = ChoreographyStructureMerger.mergeStructureOnly(existing, analyzed);

		assertEquals(2, merged.sections().size());
		assertEquals(2, merged.musicalStructure().phrases().size());
		assertEquals(0.4, merged.musicalStructure().phrases().get(0).repetitionScore());
		assertEquals(0.7, merged.musicalStructure().phrases().get(1).repetitionScore());
		assertMusicalPhraseSectionIndicesMatchPlanSections(merged);
		assertBarSectionIndicesMatchPlanSections(merged);
	}

	private static void assertMusicalPhraseSectionIndicesMatchPlanSections(ChoreographyPlan plan) {
		for (ChoreographyPlan.MusicalPhrasePlan phrase : plan.musicalStructure().phrases()) {
			double mid = (phrase.startSeconds() + phrase.endSeconds()) * 0.5;
			int expected = MusicalStructureMapper.resolveSectionIndex(plan.sections(), mid);
			assertEquals(expected, phrase.sectionIndex(),
				"phrase @" + phrase.startSeconds() + " sectionIndex mismatch");
		}
	}

	private static void assertBarSectionIndicesMatchPlanSections(ChoreographyPlan plan) {
		for (ChoreographyPlan.BarPlan bar : plan.musicalStructure().bars()) {
			double mid = (bar.startSeconds() + bar.endSeconds()) * 0.5;
			int expected = MusicalStructureMapper.resolveSectionIndex(plan.sections(), mid);
			assertEquals(expected, bar.sectionIndex(),
				"bar @" + bar.startSeconds() + " sectionIndex mismatch");
		}
	}

	private static ChoreographyPlan plan(ChoreographyPlan.SectionPlan... sections) {
		return new ChoreographyPlan(
			List.of(sections),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
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

	private static ChoreographyPlan.SectionPlan section(
		double start,
		double end,
		SectionType type,
		double confidence,
		SectionPlanSource source
	) {
		return new ChoreographyPlan.SectionPlan(start, end, type, type.name().toLowerCase(), confidence, source);
	}
}
