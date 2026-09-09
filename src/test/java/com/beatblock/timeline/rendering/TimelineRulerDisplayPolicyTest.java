package com.beatblock.timeline.rendering;

import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.TimelineMarker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineRulerDisplayPolicyTest {
	@Test
	void hidesDuplicatedAnalyzedSectionMarkerWhenChoreographyBandExists() {
		TimelineMarker analyzed = TimelineMarker.audioAnalysisSection(12.0, "Chorus");
		assertFalse(TimelineRulerDisplayPolicy.shouldDrawMarker(analyzed, true));
		assertTrue(TimelineRulerDisplayPolicy.shouldDrawMarker(analyzed, false));
	}

	@Test
	void keepsUserAuthoredCuesVisible() {
		assertTrue(TimelineRulerDisplayPolicy.shouldDrawMarker(
			TimelineMarker.manual(12.0, "Drop", MarkerType.DROP), true));
		assertTrue(TimelineRulerDisplayPolicy.shouldDrawMarker(
			TimelineMarker.manual(8.0, "Verse", MarkerType.SECTION), true));
	}

	@Test
	void compactBandsUseTooltipInsteadOfClippedText() {
		assertFalse(TimelineRulerDisplayPolicy.shouldDrawBandLabel(5.6f, 120f));
		assertTrue(TimelineRulerDisplayPolicy.shouldDrawBandLabel(14f, 120f));
		assertFalse(TimelineRulerDisplayPolicy.shouldDrawBandLabel(14f, 20f));
	}
}
