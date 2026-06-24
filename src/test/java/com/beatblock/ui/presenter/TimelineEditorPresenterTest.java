package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.command.AddTimelineAnimationEventCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEditorPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private AtomicReference<Double> lastSeek;
	private TimelineEditorPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(120.0);
		editor = new TimelineEditor(timeline);
		lastSeek = new AtomicReference<>();
		presenter = new TimelineEditorPresenter(() -> editor, lastSeek::set);
	}

	@Test
	void undoRedoDelegatesToCommandManager() {
		var event = new TimelineAnimationEvent("ev1", 1.0, 1.0, "build", "stage", 1f, Map.of());
		editor.getCommandManager().execute(new AddTimelineAnimationEventCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, event));

		assertTrue(presenter.undoRedoState().canUndo());
		assertTrue(presenter.undo());
		assertFalse(presenter.undoRedoState().canUndo());
		assertTrue(presenter.undoRedoState().canRedo());
		assertTrue(presenter.redo());
		assertEquals(1, timeline.getAutoAnimationEvents().size());
	}

	@Test
	void seekPlaybackUpdatesClockAndMusic() {
		assertTrue(presenter.seekPlayback(12.5));
		assertEquals(12.5, editor.getClock().getCurrentTimeSeconds(), 1e-9);
		assertEquals(12.5, lastSeek.get(), 1e-9);
	}

	@Test
	void loopInAdjustsOutWhenNeeded() {
		editor.getToolbarState().setLoopOutSeconds(1.0);
		assertTrue(presenter.setLoopIn(5.0));
		assertEquals(5.0, editor.getToolbarState().getLoopInSeconds(), 1e-9);
		assertTrue(editor.getToolbarState().getLoopOutSeconds() > 5.0);
		assertTrue(editor.getToolbarState().isLoop());
	}

	@Test
	void applyLoopRangeSeeksToStart() {
		assertTrue(presenter.applyLoopRange(2.0, 8.0, true));
		assertEquals(2.0, editor.getToolbarState().getLoopInSeconds(), 1e-9);
		assertEquals(8.0, editor.getToolbarState().getLoopOutSeconds(), 1e-9);
		assertEquals(2.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
		assertEquals(2.0, lastSeek.get(), 1e-9);
	}

	@Test
	void undoRedoNoOpWhenEditorMissing() {
		TimelineEditorPresenter missing = new TimelineEditorPresenter(() -> null, lastSeek::set);
		assertFalse(missing.undoRedoState().canUndo());
		assertFalse(missing.undo());
		assertFalse(missing.redo());
	}
}
