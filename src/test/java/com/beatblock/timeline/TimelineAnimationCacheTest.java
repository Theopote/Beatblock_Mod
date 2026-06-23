package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineAnimationCacheTest {

	@Test
	void markDirtyRebuildsBlockAnimationCache() {
		Timeline timeline = Timeline.createDefault();
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"ev1", 2.0, 1.0, "pulse", "stage-a", 0.8f, Map.of()));
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"ev2", 5.0, 0.5, "build", "stage-b", 1f, Map.of("buildMode", "wall")));

		assertEquals(2, timeline.getBlockAnimationEvents().size());
		assertEquals("stage-a", timeline.getBlockAnimationEvents().get(0).getTargetObjectId());
		assertEquals("wall", timeline.getBlockAnimationEvents().get(1).getParameters().get("buildMode"));
	}

	@Test
	void clearBlockAnimationEventsEmptiesCache() {
		Timeline timeline = Timeline.createDefault();
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"ev", 1.0, 1.0, "pulse", "stage", 1f, Map.of()));
		timeline.clearBlockAnimationEvents();
		assertTrue(timeline.getBlockAnimationEvents().isEmpty());
	}
}
