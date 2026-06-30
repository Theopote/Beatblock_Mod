package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.clipboard.TimelineClipboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutEventsCommandTest {

	@AfterEach
	void clearSharedClipboard() {
		TimelineClipboard.getInstance().clear();
	}

	@Test
	void executeActuallyRemovesEventFromTimeline() {
		Timeline timeline = Timeline.createDefault();
		var event = new TimelineAnimationEvent("a", 1.0, 1.0, "BlockTap", "stage", 1f, Map.of());
		new AddTimelineAnimationEventCommand(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, event).execute();
		assertEquals(1, timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK).size());

		List<TimelineAnimationEvent> selected = timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var cmd = new CutEventsCommand(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, selected);
		cmd.execute();

		assertTrue(timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK).isEmpty());
		assertEquals(1, TimelineClipboard.getInstance().getEventCount());
		assertTrue(TimelineClipboard.getInstance().isCut());
	}

	@Test
	void undoRestoresCutEventAndClearsClipboard() {
		Timeline timeline = Timeline.createDefault();
		var event = new TimelineAnimationEvent("a", 1.0, 1.0, "BlockTap", "stage", 1f, Map.of());
		new AddTimelineAnimationEventCommand(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, event).execute();

		List<TimelineAnimationEvent> selected = timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var cmd = new CutEventsCommand(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, selected);
		cmd.execute();
		cmd.undo();

		assertEquals(1, timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK).size());
		assertFalse(TimelineClipboard.getInstance().hasContent());
	}
}
