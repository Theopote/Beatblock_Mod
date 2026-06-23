package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearAnimationTrackCommandTest {

	@Test
	void executeClearsTrackAndUndoRestoresEvents() {
		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"ev1", 1.0, 1.0, "build", "stage", 1f,
			Map.of("eventOrigin", TimelineEventOrigin.MANUAL.name())));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"ev2", 3.0, 0.5, "pulse", "stage", 0.5f,
			Map.of("eventOrigin", TimelineEventOrigin.AUTO_GENERATED.name())));
		assertEquals(2, timeline.getAutoAnimationEvents().size());

		ClearAnimationTrackCommand command = new ClearAnimationTrackCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO);
		command.execute();
		assertEquals(0, timeline.getAutoAnimationEvents().size());

		command.undo();
		assertEquals(2, timeline.getAutoAnimationEvents().size());
		assertEquals(1.0, timeline.getAutoAnimationEvents().get(0).getTimeSeconds(), 1e-9);
		assertEquals(3.0, timeline.getAutoAnimationEvents().get(1).getTimeSeconds(), 1e-9);
	}

	@Test
	void ignoresNonAnimationEventsWhenSnapshotting() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = com.beatblock.timeline.TimelineOperations.addClip(track, 0, 1);
		com.beatblock.timeline.TimelineOperations.addEvent(
			clip, 0, com.beatblock.timeline.EventType.GLOBAL, Map.of("name", "x"));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"ev1", 2.0, 1.0, "build", "stage", 1f, Map.of()));

		new ClearAnimationTrackCommand(timeline, Timeline.TRACK_ID_ANIMATION_AUTO).execute();

		assertEquals(0, timeline.getAutoAnimationEvents().size());
		assertTrue(track.getClips().isEmpty());
	}
}
