package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineOperationsTest {

	@Test
	void addTrackCreatesTrackOnTimeline() {
		Timeline timeline = new Timeline();
		Track track = TimelineOperations.addTrack(timeline, "Test", TrackType.ANIMATION);

		assertNotNull(track);
		assertEquals(1, timeline.getTracks().size());
		assertEquals(track, timeline.getTrack(track.getId()));
	}

	@Test
	void addClipAndEventRoundTrip() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 1.0, 3.0);

		assertNotNull(clip);
		assertEquals(1.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(3.0, clip.getEndTimeSeconds(), 1e-9);

		TimelineEvent event = TimelineOperations.addEvent(
			clip, 1.5, EventType.ANIMATION, Map.of("animationType", "build"));
		assertNotNull(event);
		assertEquals("build", event.getParameter("animationType"));
		assertEquals(1, clip.getEvents().size());
	}

	@Test
	void moveClipPreservesDuration() {
		Clip clip = new Clip("c1", 2.0, 5.0);
		assertTrue(TimelineOperations.moveClip(clip, 10.0));
		assertEquals(10.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(13.0, clip.getEndTimeSeconds(), 1e-9);
	}

	@Test
	void removeClipAndEvent() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 1);
		TimelineEvent event = TimelineOperations.addEvent(clip, 0, EventType.ANIMATION, Map.of());

		assertTrue(TimelineOperations.removeEvent(clip, event.getId()));
		assertTrue(TimelineOperations.removeClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId()));
		assertNull(timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClip(clip.getId()));
	}

	@Test
	void moveEventUpdatesTime() {
		TimelineEvent event = new TimelineEvent("e1", 1.0, EventType.ANIMATION, Map.of());
		assertTrue(TimelineOperations.moveEvent(event, 4.0));
		assertEquals(4.0, event.getTimeSeconds(), 1e-9);
	}

	@Test
	void removeTrackReturnsFalseWhenMissing() {
		Timeline timeline = Timeline.createDefault();
		assertFalse(TimelineOperations.removeTrack(timeline, "missing-track"));
	}

	@Test
	void removeTrackRemovesAddedTrack() {
		Timeline timeline = Timeline.createDefault();
		Track extra = TimelineOperations.addTrack(timeline, "Scratch", TrackType.EVENT);
		int before = timeline.getTracks().size();

		assertTrue(TimelineOperations.removeTrack(timeline, extra.getId()));
		assertEquals(before - 1, timeline.getTracks().size());
		assertNull(timeline.getTrack(extra.getId()));
	}
}
