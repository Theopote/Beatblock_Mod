package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineTest {

	@Test
	void removeTrackRemovesMatchingTrack() {
		Timeline timeline = Timeline.createDefault();
		Track custom = TimelineOperations.addTrack(timeline, "Extra", TrackType.ANIMATION);

		assertTrue(timeline.removeTrack(custom.getId()));
		assertNull(timeline.getTrack(custom.getId()));
		assertFalse(timeline.removeTrack("missing"));
	}

	@Test
	void getAnimationEventsByOriginAggregatesBlockAndAutoTracks() {
		Timeline timeline = Timeline.createDefault();
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"manual-block", 1.0, 0.5, "pulse", "stage-a", 1f,
			Map.of("eventOrigin", TimelineEventOrigin.MANUAL.name())));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"auto-auto", 2.0, 0.5, "jump", "stage-a", 1f,
			Map.of("eventOrigin", TimelineEventOrigin.AUTO_GENERATED.name())));

		assertEquals(1, timeline.getAnimationEventsByOrigin(TimelineEventOrigin.MANUAL).size());
		assertEquals(1, timeline.getAnimationEventsByOrigin(TimelineEventOrigin.AUTO_GENERATED).size());
		assertEquals(1.0, timeline.getAnimationEventsByOrigin(TimelineEventOrigin.MANUAL).getFirst().getTimeSeconds(), 1e-9);
	}

	@Test
	void blockAnimationCacheIncludesFeatureTrackEvents() {
		Timeline timeline = Timeline.createDefault();
		String featureTrackId = Timeline.blockAnimationFeatureTrackId("kick");
		timeline.addTrack(new Track(featureTrackId, "Kick Anim", TrackType.ANIMATION));
		timeline.addAnimationEvent(featureTrackId, new TimelineAnimationEvent(
			"feat-1", 3.0, 0.4, "BlockJump", "stage-a", 0.7f, Map.of()));
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"main-1", 1.0, 0.5, "pulse", "stage-a", 1f, Map.of()));

		var events = timeline.getBlockAnimationEvents();
		assertEquals(2, events.size());
		assertEquals(1.0, events.get(0).getTimeSeconds(), 1e-9);
		assertEquals(3.0, events.get(1).getTimeSeconds(), 1e-9);
	}

	@Test
	void createDefaultIncludesCoreTracks() {
		Timeline timeline = Timeline.createDefault();
		assertTrue(timeline.getTrack(Timeline.TRACK_ID_AUDIO) != null);
		assertTrue(timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK) != null);
		assertTrue(timeline.getTrack(Timeline.TRACK_ID_CAMERA) != null);
		assertEquals(6, timeline.getTracks().size());
	}
}
