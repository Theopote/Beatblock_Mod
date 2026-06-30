package com.beatblock.timeline.command;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutTimelineEventsCommandTest {

	@Test
	void cutRemovesEventsAndUndoRestores() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		var event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of("animationType", "build"));
        String eventId = null;
        if (event != null) {
            eventId = event.getId();
        }

        SelectionState selection = new SelectionState();
		selection.selectEvent(eventId);
		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();

		CutTimelineEventsCommand command = new CutTimelineEventsCommand(
			timeline,
			selection,
			new TimelineTrackListState(),
			clipboard
		);
		command.execute();

        if (clip != null) {
            assertTrue(clip.getEvents().isEmpty());
        }
        assertEquals(1, clipboard.size());

		command.undo();
        if (clip != null) {
            assertEquals(1, clip.getEvents().size());
        }
        if (clip != null) {
            assertEquals(eventId, clip.getEvents().getFirst().getId());
        }
    }
}
