package com.beatblock.timeline.rendering;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.timeline.editor.TimelineViewState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographySectionBandLayoutTest {

	@Test
	void phraseBandSitsAboveSectionBand() {
		TimelineLayout layout = new TimelineLayout();
		layout.rulerTop = 100f;
		layout.rulerHeight = 50f;

		float phraseTop = ChoreographySectionBandLayout.phraseBandTop(layout);
		float phraseBottom = ChoreographySectionBandLayout.phraseBandBottom(layout);
		float sectionTop = ChoreographySectionBandLayout.sectionBandTop(layout);
		float rulerBottom = layout.rulerTop + layout.rulerHeight;

		assertTrue(phraseTop < phraseBottom);
		assertEquals(sectionTop, phraseBottom);
		assertTrue(sectionTop < rulerBottom);
		assertTrue(ChoreographySectionBandLayout.isInPhraseBand(phraseTop + 1f, layout));
		assertTrue(ChoreographySectionBandLayout.isInSectionBand(rulerBottom - 1f, layout));
	}
}
