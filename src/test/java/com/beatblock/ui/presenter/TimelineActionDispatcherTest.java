package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineOperations;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineActionDispatcherTest {

	@Test
	void copyAvailabilityAndExecutionUseSameEditorState() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timeline);
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		var event = TimelineOperations.addEvent(
			clip, 1.0, com.beatblock.timeline.EventType.ANIMATION, Map.of());
		editor.getSelectionState().selectEvent(event.getId());
		TimelineActionDispatcher dispatcher = dispatcher(() -> timeline, () -> editor);

		assertTrue(dispatcher.isEnabled(TimelineActionId.COPY));
		assertFalse(dispatcher.isEnabled(TimelineActionId.PASTE_AT_PLAYHEAD));
		assertTrue(dispatcher.execute(TimelineActionId.COPY).success());
		assertTrue(dispatcher.isEnabled(TimelineActionId.PASTE_AT_PLAYHEAD));
	}

	@Test
	void generatedActionPreservesPresenterFailureFeedback() {
		TimelineActionDispatcher dispatcher = dispatcher(() -> null, () -> null);

		var result = dispatcher.execute(TimelineActionId.RUN_AUTO_MAP);

		assertTrue(result.executed());
		assertFalse(result.success());
		assertEquals(com.beatblock.ui.i18n.BBTexts.get("beatblock.message.auto_map_skipped"), result.message());
	}

	@Test
	void unavailableEditActionIsNotExecuted() {
		TimelineActionDispatcher dispatcher = dispatcher(() -> null, () -> null);
		var result = dispatcher.execute(TimelineActionId.DELETE);
		assertFalse(result.executed());
		assertFalse(result.success());
	}

	private static TimelineActionDispatcher dispatcher(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> editor
	) {
		TimelineEditorPresenter editorPresenter = new TimelineEditorPresenter(editor, time -> {});
		RhythmDropPanelPresenter rhythmDrop = new RhythmDropPanelPresenter(
			() -> null, timeline, editor, () -> null);
		TimelineToolbarActionsPresenter generated = new TimelineToolbarActionsPresenter(
			timeline, editor, () -> Vec3d.ZERO, rhythmDrop);
		return new TimelineActionDispatcher(editorPresenter, editor, generated);
	}
}
