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

class PasteTimelineEventsCommandTest {

	@Test
	void pasteUndoAndRedoRoundTrip() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		var event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of("animationType", "build"));

		SelectionState selection = new SelectionState();
		selection.selectEvent(event.getId());
		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();
		TimelineInteractionClipboard.copy(clipboard, timeline, selection);
		selection.clearEvents();

		var request = new TimelineInteractionClipboard.PasteRequest(
			timeline,
			selection,
			clipboard,
			3.0,
			track.getId(),
			clip.getId(),
			new TimelineTrackListState()
		);
		PasteTimelineEventsCommand command = new PasteTimelineEventsCommand(request);
		command.execute();
		assertEquals(2, clip.getEvents().size());

		command.undo();
		assertEquals(1, clip.getEvents().size());

		command.execute();
		assertEquals(2, clip.getEvents().size());
	}
}
