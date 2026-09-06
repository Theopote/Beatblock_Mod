package com.beatblock.automap.vfx;

import com.beatblock.BeatBlock;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.playback.GlobalEventPayloadCodec;
import com.beatblock.ui.i18n.BBTexts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class GlobalEventInsertionServiceTest {

	private Timeline timeline;
	private TimelineEditor editor;

	@BeforeEach
	void setUp() {
		var context = BeatBlock.getContext();
		timeline = context.timeline();
		editor = context.timelineEditor();
		editor.getCommandManager().clear();
		editor.getSelectionState().clearAll();
		timeline.setDurationSeconds(60.0);
		editor.getClock().setDurationSeconds(60.0);
		editor.getClock().setCurrentTimeSeconds(5.0);
		var global = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		if (global != null) {
			List.copyOf(global.getClips()).forEach(c -> global.removeClip(c.getId()));
		}
	}

	@Test
	void insertManualWritesTypedScreenTintWithOneUndoAndSelection() {
		var payload = new GlobalEventPayload.ScreenTint("Warm", 0.7, 1f, 0.8f, 0.5f, 2.0);
		var result = GlobalEventInsertionService.insertManual(
			timeline,
			editor,
			new GlobalEventCreationRequest(5.0, payload)
		);

		assertTrue(result.written(), () -> "clip=" + result.clipId() + " event=" + result.eventId());
		assertEquals(1, editor.getCommandManager().undoCount());
		assertEquals(
			BBTexts.get("beatblock.undo.create_global_event"),
			editor.getCommandManager().undoDescriptionsNewestFirst().getFirst()
		);

		var clip = timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClip(result.clipId());
		assertNotNull(clip);
		assertEquals(5.0, clip.getStartTimeSeconds(), 1e-6);
		var event = clip.getEvents().stream()
			.filter(e -> e.getType() == EventType.GLOBAL)
			.findFirst()
			.orElseThrow();
		assertEquals(result.eventId(), event.getId());
		assertTrue(editor.getSelectionState().isClipSelected(result.clipId()));
		assertTrue(editor.getSelectionState().isEventSelected(result.eventId()));

		GlobalEventPayload decoded = GlobalEventPayloadCodec.decode(event.getParameters());
		assertTrue(decoded instanceof GlobalEventPayload.ScreenTint);
		GlobalEventPayload.ScreenTint tint = (GlobalEventPayload.ScreenTint) decoded;
		assertEquals(0.7, tint.intensity(), 1e-6);
		assertEquals(0.8f, tint.g(), 1e-6);

		editor.getCommandManager().undo();
		assertEquals(0, timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().size());
	}

	@Test
	void insertManualFailsWithoutTimeline() {
		var result = GlobalEventInsertionService.insertManual(
			null, null, new GlobalEventCreationRequest(1.0, GlobalEffectKind.SCREEN_TINT.defaultPayload("x")));
		assertFalse(result.written());
	}
}
