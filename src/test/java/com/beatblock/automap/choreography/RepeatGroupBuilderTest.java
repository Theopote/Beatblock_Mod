package com.beatblock.automap.choreography;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatGroupBuilderTest {

	@Test
	void groupsRepeatingPhrasesWithAnchor() {
		List<ChoreographyPlan.MusicalPhrasePlan> phrases = List.of(
			new ChoreographyPlan.MusicalPhrasePlan(0, 8, 0, 0, 0.2, -1),
			new ChoreographyPlan.MusicalPhrasePlan(8, 16, 1, 0, 0.3, -1),
			new ChoreographyPlan.MusicalPhrasePlan(16, 24, 2, 1, 0.8, -1),
			new ChoreographyPlan.MusicalPhrasePlan(24, 32, 3, 1, 0.75, -1)
		);

		List<ChoreographyPlan.MusicalPhrasePlan> annotated = RepeatGroupBuilder.annotateRepeatAnchors(phrases);
		List<ChoreographyPlan.RepeatGroup> groups = RepeatGroupBuilder.buildFromAnnotated(annotated);

		assertTrue(annotated.get(2).repeatAnchorPhraseIndex() >= 0);
		assertTrue(annotated.get(3).repeatAnchorPhraseIndex() >= 0);
		assertEquals(2, groups.size());
	}

	@Test
	void lowRepetitionPhrasesStayNovel() {
		List<ChoreographyPlan.MusicalPhrasePlan> phrases = List.of(
			new ChoreographyPlan.MusicalPhrasePlan(0, 8, 0, 0, 0.2, -1),
			new ChoreographyPlan.MusicalPhrasePlan(8, 16, 1, 0, 0.25, -1)
		);

		List<ChoreographyPlan.MusicalPhrasePlan> annotated = RepeatGroupBuilder.annotateRepeatAnchors(phrases);

		assertEquals(-1, annotated.get(0).repeatAnchorPhraseIndex());
		assertEquals(-1, annotated.get(1).repeatAnchorPhraseIndex());
		assertTrue(RepeatGroupBuilder.buildFromAnnotated(annotated).isEmpty());
	}
}
