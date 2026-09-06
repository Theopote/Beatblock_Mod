package com.beatblock.timeline.marker;

import com.beatblock.timeline.MarkerEditState;
import com.beatblock.timeline.MarkerOrigin;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.TimelineMarker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerAnalysisMergerTest {

	@Test
	void replacesOnlyGeneratedSections() {
		TimelineMarker generated = TimelineMarker.audioAnalysisSection(1.0, "SECTION A");
		TimelineMarker edited = TimelineMarker.audioAnalysisSection(5.0, "SECTION B")
			.withFields(5.2, "SECTION B*", MarkerType.SECTION, true);
		TimelineMarker drop = TimelineMarker.manual(3.0, "Drop", MarkerType.DROP);

		List<TimelineMarker> merged = MarkerAnalysisMerger.merge(
			List.of(generated, edited, drop),
			List.of(
				new MarkerAnalysisMerger.AnalyzedSection(1.0, "SECTION A2"),
				new MarkerAnalysisMerger.AnalyzedSection(8.0, "SECTION C")
			)
		);

		assertEquals(4, merged.size());
		assertTrue(merged.stream().anyMatch(m -> m.getType() == MarkerType.DROP));
		assertTrue(merged.stream().anyMatch(m ->
			m.getEditState() == MarkerEditState.USER_EDITED && m.getName().equals("SECTION B*")));
		assertTrue(merged.stream().anyMatch(m ->
			m.getEditState() == MarkerEditState.GENERATED && m.getName().equals("SECTION A2")));
		assertTrue(merged.stream().anyMatch(m ->
			m.getEditState() == MarkerEditState.GENERATED && m.getName().equals("SECTION C")));
		assertFalse(merged.stream().anyMatch(m -> m.getId().equals(generated.getId())));
	}

	@Test
	void proximitySkipsAnalyzedSectionNearProtected() {
		TimelineMarker protectedMarker = new TimelineMarker(
			"p1", 10.2, "SECTION chorus", MarkerType.SECTION,
			MarkerOrigin.AUDIO_ANALYSIS, MarkerEditState.USER_EDITED);

		List<TimelineMarker> merged = MarkerAnalysisMerger.merge(
			List.of(protectedMarker),
			List.of(
				new MarkerAnalysisMerger.AnalyzedSection(10.0, "SECTION CHORUS"),
				new MarkerAnalysisMerger.AnalyzedSection(20.0, "SECTION OUTRO")
			)
		);

		assertEquals(2, merged.size());
		assertEquals("p1", merged.get(0).getId());
		assertEquals(10.2, merged.get(0).getTimeSeconds(), 1e-9);
		assertEquals(MarkerEditState.GENERATED, merged.get(1).getEditState());
		assertEquals(20.0, merged.get(1).getTimeSeconds(), 1e-9);
	}

	@Test
	void lockedSectionIsPreservedAndSuppressesNearby() {
		TimelineMarker locked = new TimelineMarker(
			"lock", 4.0, "SECTION X", MarkerType.SECTION,
			MarkerOrigin.AUDIO_ANALYSIS, MarkerEditState.LOCKED);

		List<TimelineMarker> merged = MarkerAnalysisMerger.merge(
			List.of(locked),
			List.of(new MarkerAnalysisMerger.AnalyzedSection(4.3, "SECTION X2"))
		);

		assertEquals(1, merged.size());
		assertEquals("lock", merged.getFirst().getId());
		assertEquals(MarkerEditState.LOCKED, merged.getFirst().getEditState());
	}

	@Test
	void emptyAnalysisKeepsPreservedMarkers() {
		TimelineMarker manual = TimelineMarker.manual(1.0, "SECTION M", MarkerType.SECTION);
		List<TimelineMarker> merged = MarkerAnalysisMerger.merge(List.of(manual), List.of());
		assertEquals(1, merged.size());
		assertEquals(manual.getId(), merged.getFirst().getId());
	}
}
