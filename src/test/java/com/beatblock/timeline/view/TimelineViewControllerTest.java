package com.beatblock.timeline.view;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TimelineViewControllerTest {

	@Test
	void ownsViewTrackAndFrameSnapshotState() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timeline);
		TimelineViewController view = editor.getViewController();

		assertSame(editor.getViewState(), view.viewState());
		assertSame(editor.getToolbarState(), view.toolbarState());
		assertSame(editor.getTrackListState(), view.trackListState());

		view.viewState().setZoom(2.5f);
		assertEquals(2.5f, editor.getViewState().getZoom(), 1e-6f);

		assertSame(editor.getFrameTrackSnapshot(), view.frameTrackSnapshot());
	}
}
