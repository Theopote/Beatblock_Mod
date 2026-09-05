package com.beatblock.timeline.editing;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.command.CommandManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEventEditActionsTest {

	@Test
	void executeSubmitsUpdateCommand() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		var event = TimelineOperations.addEvent(clip, 0.5, EventType.ANIMATION, Map.of("energy", 0.5));
		CommandManager commands = new CommandManager();
		AnimationEventSnapshot before = AnimationEventSnapshot.capture(event, clip);
		AnimationEventSnapshot after = new AnimationEventSnapshot(
			1.0, Map.of("energy", 0.9), 0.0, 2.0
		);

		assertTrue(TimelineEventEditActions.execute(
			timeline, commands, track.getId(), clip.getId(), event.getId(), before, after
		));
		assertTrue(commands.canUndo());
		commands.undo();
		assertTrue(Math.abs(0.5 - event.getTimeSeconds()) < 1e-9);
	}

	@Test
	void executeRejectsMissingEventIdEvenWhenClipHasEvents() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		TimelineOperations.addEvent(clip, 0.5, EventType.ANIMATION, Map.of("energy", 0.5));
		CommandManager commands = new CommandManager();
		AnimationEventSnapshot before = AnimationEventSnapshot.captureClipOnly(clip, timeline, clip.getId());
		AnimationEventSnapshot after = new AnimationEventSnapshot(0.0, Map.of(), 1.0, 3.0);

		assertFalse(TimelineEventEditActions.execute(
			timeline, commands, track.getId(), clip.getId(), null, before, after
		));
		assertFalse(commands.canUndo());
	}

	@Test
	void executeClipOnlyWorksOnEmptyAudioClip() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		var clip = TimelineOperations.addClip(track, 0.0, 6.0);
		assertTrue(clip.getEvents().isEmpty());
		CommandManager commands = new CommandManager();

		AnimationEventSnapshot before = AnimationEventSnapshot.captureClipOnly(clip, timeline, clip.getId());
		AnimationEventSnapshot after = new AnimationEventSnapshot(
			0.0, Map.of(), 5.0, 20.0, Map.of(), Map.of(), 20.0
		);

		assertTrue(TimelineEventEditActions.executeClipOnly(
			timeline, commands, track.getId(), clip, before, after
		));
		assertEquals(5.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(20.0, clip.getEndTimeSeconds(), 1e-9);
		assertTrue(commands.canUndo());
		commands.undo();
		assertEquals(0.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(6.0, clip.getEndTimeSeconds(), 1e-9);
	}
}
