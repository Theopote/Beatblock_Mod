package com.beatblock.timeline.command;

import com.beatblock.timeline.MarkerEditState;
import com.beatblock.timeline.MarkerOrigin;
import com.beatblock.timeline.MarkerType;
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

		TimelineMarker before = timeline.getMarkers().getFirst();
		cm.execute(new MoveMarkerCommand(timeline, before, 3.5));
		assertEquals(3.5, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);

		cm.undo();
		assertEquals(1.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);

		cm.redo();
		assertEquals(3.5, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);
	}

	@Test
	void movePromotesGeneratedEditStateAndUndoRestoresIt() {
		Timeline timeline = Timeline.createDefault();
		TimelineMarker generated = new TimelineMarker(
			"s1", 1.0, "SECTION A", MarkerType.SECTION,
			MarkerOrigin.AUDIO_ANALYSIS, MarkerEditState.GENERATED);
		timeline.addMarker(generated);

		CommandManager cm = new CommandManager();
		cm.execute(new MoveMarkerCommand(timeline, generated, 4.0));
		assertEquals(MarkerEditState.USER_EDITED, timeline.getMarkers().getFirst().getEditState());

		cm.undo();
		assertEquals(MarkerEditState.GENERATED, timeline.getMarkers().getFirst().getEditState());
		assertEquals(1.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);
	}
}

class CreateUpdateDeleteMarkerCommandTest {

	@Test
	void createUpdateDeleteRoundTripWithUndo() {
		Timeline timeline = Timeline.createDefault();
		CommandManager cm = new CommandManager();
		TimelineMarker marker = TimelineMarker.manual(2.0, "Cue", MarkerType.DROP);

		CreateMarkerCommand create = new CreateMarkerCommand(timeline, marker);
		cm.execute(create);
		assertTrue(create.wasApplied());
		assertEquals(1, timeline.getMarkers().size());

		TimelineMarker before = timeline.getMarkers().getFirst();
		TimelineMarker after = before.withFields(5.0, "Cue2", MarkerType.FX, true);
		cm.execute(new UpdateMarkerCommand(timeline, before, after));
		assertEquals("Cue2", timeline.getMarkers().getFirst().getName());
		assertEquals(MarkerType.FX, timeline.getMarkers().getFirst().getType());

		cm.execute(new DeleteMarkerCommand(timeline, timeline.getMarkers().getFirst()));
		assertEquals(0, timeline.getMarkers().size());

		cm.undo();
		assertEquals(1, timeline.getMarkers().size());
		assertEquals("Cue2", timeline.getMarkers().getFirst().getName());

		cm.undo();
		assertEquals("Cue", timeline.getMarkers().getFirst().getName());
		assertEquals(2.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);

		cm.undo();
		assertEquals(0, timeline.getMarkers().size());
	}
}
