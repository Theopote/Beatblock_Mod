package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelinePanelPresenterTest {

	private final TimelinePanelPresenter presenter = new TimelinePanelPresenter();

	@Test
	void resolveDurationPrefersTimelineDuration() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(120.0);
		assertEquals(120.0, TimelinePanelPresenter.resolveDurationSeconds(timeline, 90.0), 1e-9);
	}

	@Test
	void resolveDurationFallsBackToMusicPlayer() {
		Timeline timeline = Timeline.createDefault();
		assertEquals(90.0, TimelinePanelPresenter.resolveDurationSeconds(timeline, 90.0), 1e-9);
	}

	@Test
	void resolveDurationUsesDefaultWhenNoSources() {
		Timeline timeline = Timeline.createDefault();
		assertEquals(TimelinePanelPresenter.DEFAULT_DURATION_SECONDS,
			TimelinePanelPresenter.resolveDurationSeconds(timeline, 0.0), 1e-9);
	}

	@Test
	void viewStateMarksTimelineLoadedAndBuildsDisplay() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(60.0);
		TimelineEditor editor = new TimelineEditor(timeline);

		var state = presenter.viewState(timeline, editor, 0.0);
		assertTrue(state.timelineLoaded());
		assertTrue(state.editorReady());
		assertFalse(state.positionDisplay().isBlank());
		assertEquals(60.0, state.durationSeconds(), 1e-9);
	}

	@Test
	void viewStateHandlesMissingTimeline() {
		var state = presenter.viewState(null, null, 0.0);
		assertFalse(state.timelineLoaded());
		assertFalse(state.editorReady());
	}
}
