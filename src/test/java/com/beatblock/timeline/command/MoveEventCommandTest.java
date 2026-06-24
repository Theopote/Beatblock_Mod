package com.beatblock.timeline.command;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveEventCommandTest {

	@Test
	void executeMovesEventTimeAndUndoRestores() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 1.0, 4.0);
		var event = TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of());

		MoveEventCommand command = new MoveEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), 2.0, 3.5);
		command.execute();
		assertEquals(3.5, clip.getEvent(event.getId()).getTimeSeconds(), 1e-9);

		command.undo();
		assertEquals(2.0, clip.getEvent(event.getId()).getTimeSeconds(), 1e-9);
	}

	@Test
	void noOpWhenTrackOrClipMissing() {
		Timeline timeline = Timeline.createDefault();
		MoveEventCommand command = new MoveEventCommand(
			timeline, "missing", "clip", "event", 1.0, 2.0);
		command.execute();
		command.undo();
	}

	@Test
	void mergesContinuousMovesWhenNewMatchesPreviousEnd() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 5.0);
		var event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());

		long anchor = System.currentTimeMillis();
		var first = new MoveEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), 1.0, 2.0, anchor);
		var second = new MoveEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), 2.0, 3.5, anchor);

		assertTrue(first.canMergeWith(second));
		MoveEventCommand merged = (MoveEventCommand) first.mergeWith(second);
		merged.execute();
		assertEquals(3.5, event.getTimeSeconds(), 1e-9);
		merged.undo();
		assertEquals(1.0, event.getTimeSeconds(), 1e-9);
	}

	@Test
	void doesNotMergeWhenTimesAreNotContinuous() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 5.0);
		var event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());

		long anchor = System.currentTimeMillis();
		var first = new MoveEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), 1.0, 2.0, anchor);
		var second = new MoveEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), 1.5, 3.0, anchor);

		assertFalse(first.canMergeWith(second));
	}
}
