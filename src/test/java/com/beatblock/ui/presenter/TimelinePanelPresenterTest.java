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

	@Test
	void viewStateWorksWithoutEditor() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(45.0);
		timeline.setMetadata("bpm", 128.0);

		var state = presenter.viewState(timeline, null, 30.0);

		assertTrue(state.timelineLoaded());
		assertFalse(state.editorReady());
		assertEquals(45.0, state.durationSeconds(), 1e-9);
		assertEquals(0.0, state.currentTimeSeconds(), 1e-9);
		assertEquals(128.0, state.bpm(), 1e-9);
		assertFalse(state.positionDisplay().isBlank());
	}

	@Test
	void viewStateReflectsEditorClock() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(90.0);
		TimelineEditor editor = new TimelineEditor(timeline);
		editor.getClock().seek(12.5);

		var state = presenter.viewState(timeline, editor, 0.0);

		assertEquals(12.5, state.currentTimeSeconds(), 1e-9);
		assertEquals(90.0, state.durationSeconds(), 1e-9);
	}

	@Test
	void resolveDurationSecondsHandlesNullTimeline() {
		assertEquals(75.0, TimelinePanelPresenter.resolveDurationSeconds(null, 75.0), 1e-9);
		assertEquals(TimelinePanelPresenter.DEFAULT_DURATION_SECONDS,
			TimelinePanelPresenter.resolveDurationSeconds(null, 0.0), 1e-9);
	}
}
