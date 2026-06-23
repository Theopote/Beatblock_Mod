package com.beatblock.timeline.editor;

import com.beatblock.timeline.Timeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TimelineEditorStateTest {

	@Test
	void wiresSubsystemsAndTrackUiStates() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(120.0);
		TimelineEditorState state = new TimelineEditorState(timeline);

		assertSame(timeline, state.getTimeline());
		assertNotNull(state.getClock());
		assertNotNull(state.getViewState());
		assertNotNull(state.getSelectionState());
		assertNotNull(state.getInteractionState());
		assertNotNull(state.getSelectionBox());
		assertNotNull(state.getTrackUIState(Timeline.TRACK_ID_AUDIO));
		assertEquals(120.0, state.getClock().getDurationSeconds(), 1e-9);
	}

	@Test
	void syncClockDurationFollowsTimeline() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditorState state = new TimelineEditorState(timeline);
		timeline.setDurationSeconds(90.0);
		state.syncClockDuration();
		assertEquals(90.0, state.getClock().getDurationSeconds(), 1e-9);
	}

	@Test
	void getTrackUiStateCreatesMissingEntry() {
		TimelineEditorState state = new TimelineEditorState(Timeline.createDefault());
		TrackUIState ui = state.getTrackUIState("custom-track");
		assertNotNull(ui);
		assertSame(ui, state.getTrackUIState("custom-track"));
	}
}
