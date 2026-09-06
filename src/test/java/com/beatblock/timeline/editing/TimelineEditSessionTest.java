package com.beatblock.timeline.editing;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineOperations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEditSessionTest {

	@Test
	void ownsSelectionClipboardAndCommandHistory() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timeline);
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		var event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());
		TimelineEditSession session = editor.getEditSession();

		assertSame(editor.getSelectionState(), session.selection());
		assertFalse(session.hasSelection());
		session.selection().selectEvent(event.getId());
		assertTrue(session.hasSelection());

		session.copy();
		assertTrue(session.hasClipboardContent());
		session.pasteAt(4.0);
		assertEquals(1, session.commands().undoCount());

		session.selection().selectEvent(event.getId());
		assertTrue(session.duplicateSelection());
		assertEquals(2, session.commands().undoCount());

		session.selection().clearEvents();
		session.selection().selectClip(clip.getId());
		assertTrue(session.splitAt(1.0));
		assertEquals(3, session.commands().undoCount());

		session.clearHistory();
		assertEquals(0, session.commands().undoCount());
	}
}
