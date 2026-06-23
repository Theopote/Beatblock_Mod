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
}
