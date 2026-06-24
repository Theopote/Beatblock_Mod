package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandManagerTest {

	@Test
	void executeUndoRedoCycle() {
		Timeline timeline = Timeline.createDefault();
		CommandManager manager = new CommandManager();
		var event = new TimelineAnimationEvent("ev1", 1.0, 1.0, "build", "stage", 1f, Map.of());

		manager.execute(new AddTimelineAnimationEventCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, event));
		assertEquals(1, timeline.getAutoAnimationEvents().size());
		assertTrue(manager.canUndo());
		assertFalse(manager.canRedo());

		manager.undo();
		assertTrue(timeline.getAutoAnimationEvents().isEmpty());
		assertFalse(manager.canUndo());
		assertTrue(manager.canRedo());

		manager.redo();
		assertEquals(1, timeline.getAutoAnimationEvents().size());
	}

	@Test
	void clearEmptiesStacks() {
		CommandManager manager = new CommandManager();
		AtomicBoolean executed = new AtomicBoolean();
		manager.execute(new Command() {
			@Override public void execute() { executed.set(true); }
			@Override public void undo() { executed.set(false); }
		});
		assertTrue(manager.canUndo());
		manager.clear();
		assertFalse(manager.canUndo());
		assertFalse(manager.canRedo());
	}

	@Test
	void mergesConsecutiveUpdateAnimationEventCommandsIntoSingleUndo() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = com.beatblock.timeline.TimelineOperations.addClip(track, 1.0, 4.0);
		var event = com.beatblock.timeline.TimelineOperations.addEvent(
			clip, 2.0, com.beatblock.timeline.EventType.ANIMATION, Map.of("energy", 0.5));

		var before = com.beatblock.timeline.editing.AnimationEventSnapshot.capture(event, clip);
		var midParams = new java.util.HashMap<>(event.getParameters());
		midParams.put("energy", 0.7);
		var mid = new com.beatblock.timeline.editing.AnimationEventSnapshot(2.0, midParams, 1.0, 4.0);
		var endParams = new java.util.HashMap<>(event.getParameters());
		endParams.put("energy", 0.9);
		var end = new com.beatblock.timeline.editing.AnimationEventSnapshot(2.0, endParams, 1.0, 4.0);

		long anchor = System.currentTimeMillis();
		CommandManager manager = new CommandManager();
		manager.execute(new UpdateAnimationEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), before, mid, anchor));
		manager.execute(new UpdateAnimationEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), mid, end, anchor));

		assertEquals(0.9, event.getParameter("energy"));
		assertTrue(manager.canUndo());
		manager.undo();
		assertEquals(0.5, event.getParameter("energy"));
		assertFalse(manager.canUndo());
	}

	@Test
	void doesNotMergeUpdateCommandsForDifferentEvents() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = com.beatblock.timeline.TimelineOperations.addClip(track, 0.0, 6.0);
		var eventA = com.beatblock.timeline.TimelineOperations.addEvent(
			clip, 1.0, com.beatblock.timeline.EventType.ANIMATION, Map.of("energy", 0.1));
		var eventB = com.beatblock.timeline.TimelineOperations.addEvent(
			clip, 3.0, com.beatblock.timeline.EventType.ANIMATION, Map.of("energy", 0.2));

		var beforeA = com.beatblock.timeline.editing.AnimationEventSnapshot.capture(eventA, clip);
		var afterAParams = new java.util.HashMap<>(eventA.getParameters());
		afterAParams.put("energy", 0.5);
		var afterA = new com.beatblock.timeline.editing.AnimationEventSnapshot(1.0, afterAParams, 0.0, 6.0);
		var beforeB = com.beatblock.timeline.editing.AnimationEventSnapshot.capture(eventB, clip);
		var afterBParams = new java.util.HashMap<>(eventB.getParameters());
		afterBParams.put("energy", 0.8);
		var afterB = new com.beatblock.timeline.editing.AnimationEventSnapshot(3.0, afterBParams, 0.0, 6.0);

		long anchor = System.currentTimeMillis();
		CommandManager manager = new CommandManager();
		manager.execute(new UpdateAnimationEventCommand(
			timeline, track.getId(), clip.getId(), eventA.getId(), beforeA, afterA, anchor));
		manager.execute(new UpdateAnimationEventCommand(
			timeline, track.getId(), clip.getId(), eventB.getId(), beforeB, afterB, anchor));

		manager.undo();
		assertEquals(0.5, eventA.getParameter("energy"));
		assertEquals(0.2, eventB.getParameter("energy"));
		manager.undo();
		assertEquals(0.1, eventA.getParameter("energy"));
	}

	@Test
	void mergesConsecutiveMoveEventCommandsIntoSingleUndo() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = com.beatblock.timeline.TimelineOperations.addClip(track, 1.0, 6.0);
		var event = com.beatblock.timeline.TimelineOperations.addEvent(
			clip, 2.0, com.beatblock.timeline.EventType.ANIMATION, Map.of());

		long anchor = System.currentTimeMillis();
		CommandManager manager = new CommandManager();
		manager.execute(new MoveEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), 2.0, 3.0, anchor));
		manager.execute(new MoveEventCommand(
			timeline, track.getId(), clip.getId(), event.getId(), 3.0, 4.5, anchor));

		assertEquals(4.5, event.getTimeSeconds(), 1e-9);
		manager.undo();
		assertEquals(2.0, event.getTimeSeconds(), 1e-9);
		assertFalse(manager.canUndo());
	}
}
