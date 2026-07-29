package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelinePerTrackEventCacheTest {

	@Test
	void getAnimationEventsReturnsCachedViewAndIsStableAcrossCalls() {
		Timeline timeline = Timeline.createDefault();
		String featureId = Timeline.blockAnimationFeatureTrackId("kick");
		timeline.addTrack(new Track(featureId, "Kick", TrackType.ANIMATION));
		timeline.addAnimationEvent(featureId, new TimelineAnimationEvent(
			"e1", 1.0, 0.5, "pulse", "s", 1f, Map.of()));

		List<TimelineAnimationEvent> a = timeline.getAnimationEvents(featureId);
		List<TimelineAnimationEvent> b = timeline.getAnimationEvents(featureId);
		assertEquals(1, a.size());
		assertSame(a, b);

		timeline.addAnimationEvent(featureId, new TimelineAnimationEvent(
			"e2", 2.0, 0.5, "pulse", "s", 1f, Map.of()));
		List<TimelineAnimationEvent> c = timeline.getAnimationEvents(featureId);
		assertEquals(2, c.size());
		assertTrue(c.get(0).getTimeSeconds() <= c.get(1).getTimeSeconds());
	}

	@Test
	void missingTrackReturnsEmpty() {
		Timeline timeline = Timeline.createDefault();
		assertTrue(timeline.getAnimationEvents("no-such-track").isEmpty());
	}
}
