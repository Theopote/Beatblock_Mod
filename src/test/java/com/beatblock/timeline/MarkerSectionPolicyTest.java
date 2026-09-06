package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerSectionPolicyTest {

	@Test
	void addingSectionAtSameTimeReplacesUnlockedOccupant() {
		Timeline timeline = Timeline.createDefault();
		TimelineMarker first = TimelineMarker.manual(10.0, "SECTION A", MarkerType.SECTION);
		timeline.addMarker(first);
		TimelineMarker second = TimelineMarker.manual(10.0, "SECTION B", MarkerType.SECTION);
		timeline.addMarker(second);

		assertEquals(1, timeline.getMarkers().size());
		assertEquals("SECTION B", timeline.getMarkers().getFirst().getName());
		assertEquals(second.getId(), timeline.getMarkers().getFirst().getId());
	}

	@Test
	void addingSectionAtSameTimeRejectsWhenLocked() {
		Timeline timeline = Timeline.createDefault();
		TimelineMarker locked = new TimelineMarker(
			"lock", 10.0, "SECTION A", MarkerType.SECTION,
			MarkerOrigin.MANUAL, MarkerEditState.LOCKED);
		timeline.addMarker(locked);
		timeline.addMarker(TimelineMarker.manual(10.0, "SECTION B", MarkerType.SECTION));

		assertEquals(1, timeline.getMarkers().size());
		assertEquals("lock", timeline.getMarkers().getFirst().getId());
	}

	@Test
	void nonSectionMarkersMayShareTimestamp() {
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(TimelineMarker.manual(5.0, "A", MarkerType.GENERIC));
		timeline.addMarker(TimelineMarker.manual(5.0, "B", MarkerType.DROP));
		assertEquals(2, timeline.getMarkers().size());
	}

	@Test
	void replaceMarkerMovingOntoLockedSectionFails() {
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(new TimelineMarker(
			"lock", 10.0, "SECTION A", MarkerType.SECTION,
			MarkerOrigin.MANUAL, MarkerEditState.LOCKED));
		TimelineMarker moving = TimelineMarker.manual(2.0, "SECTION B", MarkerType.SECTION);
		timeline.addMarker(moving);

		TimelineMarker attempted = moving.withFields(10.0, "SECTION B", MarkerType.SECTION, true);
		assertFalse(timeline.replaceMarker(attempted));
		assertEquals(2.0, timeline.getMarkers().stream()
			.filter(m -> m.getId().equals(moving.getId()))
			.findFirst().orElseThrow()
			.getTimeSeconds(), 1e-9);
		assertTrue(MarkerEditPolicy.isLocked(timeline.getMarkers().stream()
			.filter(m -> m.getId().equals("lock"))
			.findFirst().orElseThrow()));
	}
}
