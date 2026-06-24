package com.beatblock.ui.presenter;

import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PresenterFactoriesContextTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private BeatBlockContext context;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.build();
		PresenterFactories.setContextSourceForTests(() -> context);
	}

	@AfterEach
	void tearDown() {
		PresenterFactories.resetContextSourceForTests();
	}

	@Test
	void menuBarPresenterUsesInjectedTimeline() {
		timeline.setMetadata("projectPath", "D:/proj/show.osc");
		MenuBarPresenter presenter = PresenterFactories.menuBarPresenter();
		assertEquals("D:/proj/show.osc", presenter.defaultSaveProjectPath());
	}

	@Test
	void markerPanelPresenterReadsInjectedTimeline() {
		MarkerPanelPresenter presenter = PresenterFactories.markerPanelPresenter();
		assertSame(timeline, presenter.currentTimeline());
	}
}
