package com.beatblock.timeline.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionStateTest {

	@Test
	void selectAndDeselectEventsClipsAndTracks() {
		SelectionState state = new SelectionState();
		state.selectEvent("e1");
		state.selectClip("c1");
		state.selectTrack("t1");

		assertTrue(state.isEventSelected("e1"));
		assertTrue(state.isClipSelected("c1"));
		assertTrue(state.isTrackSelected("t1"));

		state.deselectEvent("e1");
		state.deselectClip("c1");
		state.deselectTrack("t1");

		assertFalse(state.isEventSelected("e1"));
		assertFalse(state.isClipSelected("c1"));
		assertFalse(state.isTrackSelected("t1"));
	}

	@Test
	void clearAllEmptiesEverySelectionSet() {
		SelectionState state = new SelectionState();
		state.selectEvent("e1");
		state.selectClip("c1");
		state.selectTrack("t1");
		state.clearAll();

		assertTrue(state.getSelectedEvents().isEmpty());
		assertTrue(state.getSelectedClips().isEmpty());
		assertTrue(state.getSelectedTracks().isEmpty());
	}

	@Test
	void ignoresNullIds() {
		SelectionState state = new SelectionState();
		state.selectEvent(null);
		state.selectClip(null);
		state.selectTrack(null);
		assertFalse(state.isEventSelected(null));
	}
}
