package com.beatblock.timeline.command;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AddEventCommandTest {

	@Test
	void executeAddsEventToClipAndUndoRemoves() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		var event = new TimelineEvent("evt-1", 1.5, EventType.ANIMATION, Map.of("energy", 0.5f));

		AddEventCommand command = new AddEventCommand(timeline, track.getId(), clip.getId(), event);
		command.execute();
		assertNotNull(clip.getEvent("evt-1"));
		assertEquals(1.5, clip.getEvent("evt-1").getTimeSeconds(), 1e-9);

		command.undo();
		assertNull(clip.getEvent("evt-1"));
	}

	@Test
	void executeIsIdempotentWhenAlreadyDone() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		var event = new TimelineEvent("evt-dup", 0.5, EventType.ANIMATION, Map.of());

		AddEventCommand command = new AddEventCommand(timeline, track.getId(), clip.getId(), event);
		command.execute();
		command.execute();
		assertEquals(1, clip.getEvents().size());
	}
}
