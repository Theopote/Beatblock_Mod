package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveMarkerCommandTest {

	@Test
	void executeAndUndoMoveMarkerTime() {
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(new TimelineMarker("m1", 1.0, "Intro"));
		CommandManager cm = new CommandManager();

		cm.execute(new MoveMarkerCommand(timeline, "m1", 1.0, 3.5, "Intro"));
		assertEquals(3.5, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);

		assertTrue(cm.canUndo());
		cm.undo();
		assertEquals(1.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);

		cm.redo();
		assertEquals(3.5, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);
	}
}
