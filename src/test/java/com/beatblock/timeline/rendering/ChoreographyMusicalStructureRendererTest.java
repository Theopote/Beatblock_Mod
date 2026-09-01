package com.beatblock.timeline.rendering;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.timeline.editor.TimelineViewState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
