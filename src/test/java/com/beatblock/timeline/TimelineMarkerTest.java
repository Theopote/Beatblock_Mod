package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineMarkerTest {

	@Test
	void clampsNegativeTimeAndDefaultsNullFields() {
		TimelineMarker marker = new TimelineMarker(-1.0, null, null);
		assertEquals(0.0, marker.getTimeSeconds(), 1e-9);
		assertEquals("", marker.getName());
		assertEquals(MarkerType.GENERIC, marker.getType());
		assertEquals(MarkerOrigin.MANUAL, marker.getOrigin());
		assertEquals(MarkerEditState.USER_EDITED, marker.getEditState());
		assertNotNull(marker.getId());
		assertFalse(marker.getId().isBlank());
	}

	@Test
	void preservesExplicitIdAndType() {
		TimelineMarker marker = new TimelineMarker("mk-1", 3.5, "Drop", MarkerType.DROP);
		assertEquals("mk-1", marker.getId());
		assertEquals(3.5, marker.getTimeSeconds(), 1e-9);
		assertEquals("Drop", marker.getName());
		assertEquals(MarkerType.DROP, marker.getType());
	}

	@Test
	void audioAnalysisSectionUsesGeneratedProvenance() {
		TimelineMarker marker = TimelineMarker.audioAnalysisSection(1.25, "SECTION INTRO");
		assertEquals(MarkerType.SECTION, marker.getType());
		assertEquals(MarkerOrigin.AUDIO_ANALYSIS, marker.getOrigin());
		assertEquals(MarkerEditState.GENERATED, marker.getEditState());
	}

	@Test
	void withFieldsPromotesGeneratedToUserEdited() {
		TimelineMarker generated = TimelineMarker.audioAnalysisSection(1.0, "SECTION A");
		TimelineMarker edited = generated.withFields(2.0, "SECTION A", MarkerType.SECTION, true);
		assertEquals(2.0, edited.getTimeSeconds(), 1e-9);
		assertEquals(MarkerEditState.USER_EDITED, edited.getEditState());
		assertEquals(MarkerOrigin.AUDIO_ANALYSIS, edited.getOrigin());
	}

	@Test
	void withFieldsLiveDoesNotPromote() {
		TimelineMarker generated = TimelineMarker.audioAnalysisSection(1.0, "SECTION A");
		TimelineMarker live = generated.withTimeSeconds(2.0, false);
		assertEquals(2.0, live.getTimeSeconds(), 1e-9);
		assertEquals(MarkerEditState.GENERATED, live.getEditState());
	}
}
