package com.beatblock.automap.choreography;

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
