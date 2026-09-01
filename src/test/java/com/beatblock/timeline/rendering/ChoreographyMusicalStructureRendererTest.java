package com.beatblock.timeline.rendering;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.timeline.editor.TimelineViewState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ChoreographyMusicalStructureRendererTest {

	@Test
	void findsPhraseIndexInPhraseBand() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, com.beatblock.automap.engine.SectionType.VERSE, "verse")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			com.beatblock.automap.choreography.DensityCurve.uniform(1.0),
			List.of(),
			new ChoreographyPlan.MusicalStructure(
				List.of(new ChoreographyPlan.BarPlan(0, 4, 0, 0)),
				List.of(
					new ChoreographyPlan.MusicalPhrasePlan(0, 8, 0, 0, 0.2, -1),
					new ChoreographyPlan.MusicalPhrasePlan(8, 16, 1, 0, 0.7, 0)
				),
				List.of()
			)
		);

		TimelineViewState view = new TimelineViewState();
		view.setZoom(20f);
		view.setViewStartTimeSeconds(0);
		view.setViewEndTimeSeconds(16);

		TimelineLayout layout = new TimelineLayout();
		layout.rulerTop = 100f;
		layout.rulerHeight = 50f;
		layout.contentLeft = 200f;
		layout.contentWidth = 400f;

		float phraseY = ChoreographySectionBandLayout.phraseBandTop(layout) + 2f;
		float phrase0X = layout.contentLeft + view.timeToScreen(4.0);
		float phrase1X = layout.contentLeft + view.timeToScreen(12.0);

		assertEquals(0, ChoreographyMusicalStructureRenderer.findPhraseIndexAt(plan, view, layout, phrase0X, phraseY));
		assertEquals(1, ChoreographyMusicalStructureRenderer.findPhraseIndexAt(plan, view, layout, phrase1X, phraseY));
		assertEquals(-1, ChoreographyMusicalStructureRenderer.findPhraseIndexAt(
			plan, view, layout, phrase0X, ChoreographySectionBandLayout.sectionBandTop(layout) + 2f));
	}

	@Test
	void resolvesSectionIndexFromPhraseClick() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 8, com.beatblock.automap.engine.SectionType.VERSE, "verse"),
				new ChoreographyPlan.SectionPlan(8, 16, com.beatblock.automap.engine.SectionType.CHORUS, "chorus")
			),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			com.beatblock.automap.choreography.DensityCurve.uniform(1.0),
			List.of(),
			new ChoreographyPlan.MusicalStructure(
				List.of(),
				List.of(
					new ChoreographyPlan.MusicalPhrasePlan(0, 8, 0, 0, 0.2, -1),
					new ChoreographyPlan.MusicalPhrasePlan(8, 16, 1, 1, 0.7, 0)
				),
				List.of()
			)
		);

		assertEquals(0, ChoreographyMusicalStructureRenderer.resolveSectionIndexForPhrase(plan, 0));
		assertEquals(1, ChoreographyMusicalStructureRenderer.resolveSectionIndexForPhrase(plan, 1));

		ChoreographyPlan.MusicalPhrasePlan unmapped = new ChoreographyPlan.MusicalPhrasePlan(0, 8, 0, -1, 0.2, -1);
		ChoreographyPlan planWithFallback = new ChoreographyPlan(
			plan.sections(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			com.beatblock.automap.choreography.DensityCurve.uniform(1.0),
			List.of(),
			new ChoreographyPlan.MusicalStructure(List.of(), List.of(unmapped), List.of())
		);
		assertEquals(0, ChoreographyMusicalStructureRenderer.resolveSectionIndexForPhrase(planWithFallback, 0));
	}

	@Test
	void repeatGroupMembersShareBorderColor() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 32, com.beatblock.automap.engine.SectionType.VERSE, "verse")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			com.beatblock.automap.choreography.DensityCurve.uniform(1.0),
			List.of(),
			new ChoreographyPlan.MusicalStructure(
				List.of(),
				List.of(
					new ChoreographyPlan.MusicalPhrasePlan(0, 8, 0, 0, 0.2, -1),
					new ChoreographyPlan.MusicalPhrasePlan(8, 16, 1, 0, 0.3, -1),
					new ChoreographyPlan.MusicalPhrasePlan(16, 24, 2, 0, 0.8, 0),
					new ChoreographyPlan.MusicalPhrasePlan(24, 32, 3, 0, 0.75, 0)
				),
				List.of(
					new ChoreographyPlan.RepeatGroup(0, 0, List.of(0, 2), 0.8),
					new ChoreographyPlan.RepeatGroup(1, 1, List.of(1, 3), 0.75)
				)
			)
		);

		ChoreographyPlan.MusicalPhrasePlan anchor0 = plan.musicalStructure().phrases().get(0);
		ChoreographyPlan.MusicalPhrasePlan repeat2 = plan.musicalStructure().phrases().get(2);
		ChoreographyPlan.MusicalPhrasePlan anchor1 = plan.musicalStructure().phrases().get(1);
		ChoreographyPlan.MusicalPhrasePlan repeat3 = plan.musicalStructure().phrases().get(3);

		int borderAlpha = 0xAA;
		int group0AnchorBorder = ChoreographyMusicalStructureRenderer.borderColorForPhrase(plan, anchor0, 0, borderAlpha);
		int group0RepeatBorder = ChoreographyMusicalStructureRenderer.borderColorForPhrase(plan, repeat2, 0, borderAlpha);
		int group1AnchorBorder = ChoreographyMusicalStructureRenderer.borderColorForPhrase(plan, anchor1, 1, borderAlpha);
		int group1RepeatBorder = ChoreographyMusicalStructureRenderer.borderColorForPhrase(plan, repeat3, 1, borderAlpha);

		assertEquals(group0AnchorBorder, group0RepeatBorder);
		assertEquals(group1AnchorBorder, group1RepeatBorder);
		assertNotEquals(group0AnchorBorder, group1AnchorBorder);
		assertEquals(0, ChoreographyMusicalStructureRenderer.repeatGroupIdForPhrase(plan, 0));
		assertEquals(0, ChoreographyMusicalStructureRenderer.repeatGroupIdForPhrase(plan, 2));
		assertEquals(1, ChoreographyMusicalStructureRenderer.repeatGroupIdForPhrase(plan, 3));
		assertEquals(-1, ChoreographyMusicalStructureRenderer.repeatGroupIdForPhrase(plan, 99));
	}
}
