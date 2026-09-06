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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class EnvironmentPresetInsertionTest {

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
		editor.getClock().setCurrentTimeSeconds(10.0);
		var global = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		if (global != null) {
			List.copyOf(global.getClips()).forEach(c -> global.removeClip(c.getId()));
		}
	}

	@Test
	void stormPresetIsThreeCuesButOneUndo() {
		EnvironmentPreset storm = EnvironmentPreset.storm();
		assertEquals(3, storm.componentCount());

		var result = GlobalEventInsertionService.applyPreset(timeline, editor, storm, 10.0);

		assertTrue(result.written());
		assertEquals(3, result.writtenCount());
		assertEquals(1, editor.getCommandManager().undoCount());
		assertEquals(
			BBTexts.get("beatblock.undo.apply_environment_preset", storm.displayName()),
			editor.getCommandManager().undoDescriptionsNewestFirst().getFirst()
		);
		assertEquals(3, timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().size());
		assertEquals(3, editor.getSelectionState().getSelectedEvents().size());
		assertTrue(editor.getSelectionState().getSelectedEvents().containsAll(result.eventIds()));

		List<GlobalEventPayload> payloads = result.eventIds().stream()
			.map(id -> {
				var clip = timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().stream()
					.filter(c -> c.getEvents().stream().anyMatch(e -> id.equals(e.getId())))
					.findFirst()
					.orElseThrow();
				var event = clip.getEvents().stream()
					.filter(e -> id.equals(e.getId()) && e.getType() == EventType.GLOBAL)
					.findFirst()
					.orElseThrow();
				return GlobalEventPayloadCodec.decode(event.getParameters());
			})
			.toList();
		assertInstanceOf(GlobalEventPayload.EnvironmentLighting.class, payloads.get(0));
		assertInstanceOf(GlobalEventPayload.LocalVisualWeather.class, payloads.get(1));
		assertInstanceOf(GlobalEventPayload.ScreenTint.class, payloads.get(2));

		editor.getCommandManager().undo();
		assertEquals(0, timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().size());
		assertEquals(0, editor.getCommandManager().undoCount());
	}

	@Test
	void applyPresetDoesNotStackOneUndoPerComponent() {
		var result = GlobalEventInsertionService.applyPreset(
			timeline, editor, EnvironmentPreset.warmSunset(), 4.0);
		assertEquals(2, result.writtenCount());
		assertEquals(1, editor.getCommandManager().undoCount());
	}

	@Test
	void missingTimelineReturnsEmpty() {
		var result = GlobalEventInsertionService.applyPreset(
			null, null, EnvironmentPreset.storm(), 1.0);
		assertFalse(result.written());
	}

	@Test
	void catalogContainsExpectedIds() {
		assertTrue(EnvironmentPreset.find("storm").isPresent());
		assertTrue(EnvironmentPreset.find("night_performance").isPresent());
		assertTrue(EnvironmentPreset.find("warm_sunset").isPresent());
		assertTrue(EnvironmentPreset.find("concert_flash").isPresent());
		assertTrue(EnvironmentPreset.find("missing").isEmpty());
	}
}
