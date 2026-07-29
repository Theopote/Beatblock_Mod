package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageEventIndexTest {

	private static TimelineAnimationEvent at(String id, double t) {
		return new TimelineAnimationEvent(id, t, 0.5, "pulse", "stage", 1f, Map.of());
	}

	@Test
	void lowerBoundFindsFirstAtOrAfterTime() {
		List<TimelineAnimationEvent> events = List.of(
			at("a", 1.0),
			at("b", 2.0),
			at("c", 2.0),
			at("d", 4.0)
		);
		assertEquals(0, StageEventIndex.lowerBound(events, 0.0));
		assertEquals(0, StageEventIndex.lowerBound(events, 1.0));
		assertEquals(1, StageEventIndex.lowerBound(events, 1.5));
		assertEquals(1, StageEventIndex.lowerBound(events, 2.0));
		assertEquals(3, StageEventIndex.lowerBound(events, 3.0));
		assertEquals(4, StageEventIndex.lowerBound(events, 5.0));
	}

	@Test
	void upperBoundFindsFirstStrictlyAfterTime() {
		List<TimelineAnimationEvent> events = List.of(
			at("a", 1.0),
			at("b", 2.0),
			at("c", 2.0),
			at("d", 4.0)
		);
		assertEquals(1, StageEventIndex.upperBound(events, 1.0));
		assertEquals(3, StageEventIndex.upperBound(events, 2.0));
		assertEquals(4, StageEventIndex.upperBound(events, 4.0));
		assertEquals(0, StageEventIndex.upperBound(List.of(), 1.0));
	}

	@Test
	void cursorAfterTimeUsesEpsilon() {
		List<TimelineAnimationEvent> events = List.of(at("a", 1.0), at("b", 2.0));
		assertEquals(1, StageEventIndex.cursorAfterTime(events, 1.0, 1e-4));
		assertEquals(2, StageEventIndex.cursorAfterTime(events, 2.0, 1e-4));
	}
}
