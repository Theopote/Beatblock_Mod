package com.beatblock.ui.presenter;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
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
	void bindingMapRequiresConfirmationWhenGeneratedEventsExist() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timeline);
		String trackId = Timeline.blockAnimationFeatureTrackId("kick");
		timeline.addTrack(new Track(trackId, "kick", TrackType.ANIMATION));
		var clip = TimelineOperations.addClip(timeline, trackId, 0, 4);
		TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of(
			"generatedBy", AnimationBindingEngine.GENERATED_BY_MARK
		));

		TimelineActionDispatcher dispatcher = dispatcher(() -> timeline, () -> editor);
		assertTrue(dispatcher.requiresConfirmation(TimelineActionId.RUN_BINDING_MAP));
		assertEquals(1, dispatcher.affectedEventCount(TimelineActionId.RUN_BINDING_MAP));
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
