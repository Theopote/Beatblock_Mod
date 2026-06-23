package com.beatblock.timeline.command;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editing.AnimationEventSnapshot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UpdateAnimationEventCommandTest {

	@Test
	void executeAppliesSnapshotAndUndoRestores() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 1.0, 4.0);
		var event = TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of(
			"energy", 0.5,
			"dispatchModel", "BURST"
		));

		Map<String, Object> updatedParams = new HashMap<>(event.getParameters());
		updatedParams.put("energy", 0.9);
		updatedParams.put("durationSeconds", 1.5);
		AnimationEventSnapshot before = AnimationEventSnapshot.capture(event, clip);
		AnimationEventSnapshot after = new AnimationEventSnapshot(
			2.5, updatedParams, 2.5, 4.0
		);

		UpdateAnimationEventCommand command = new UpdateAnimationEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), before, after);
		command.execute();

		assertEquals(2.5, event.getTimeSeconds(), 1e-9);
		assertEquals(0.9, event.getParameter("energy"));
		assertEquals(2.5, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(4.0, clip.getEndTimeSeconds(), 1e-9);

		command.undo();
		assertEquals(2.0, event.getTimeSeconds(), 1e-9);
		assertEquals(0.5, event.getParameter("energy"));
		assertEquals(1.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(4.0, clip.getEndTimeSeconds(), 1e-9);
	}

	@Test
	void undoRemovesParametersAddedByExecute() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		var event = TimelineOperations.addEvent(clip, 0.5, EventType.ANIMATION, Map.of("dispatchModel", "BURST"));

		Map<String, Object> stepParams = new HashMap<>(event.getParameters());
		stepParams.put("dispatchModel", "STEP");
		stepParams.put("blocksPerBeat", 2);
		AnimationEventSnapshot before = AnimationEventSnapshot.capture(event, clip);
		AnimationEventSnapshot after = new AnimationEventSnapshot(
			0.5, stepParams, 0.0, 2.0
		);

		UpdateAnimationEventCommand command = new UpdateAnimationEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), before, after);
		command.execute();
		assertEquals("STEP", event.getParameter("dispatchModel"));

		command.undo();
		assertEquals("BURST", event.getParameter("dispatchModel"));
		assertFalse(event.getParameters().containsKey("blocksPerBeat"));
	}
}
