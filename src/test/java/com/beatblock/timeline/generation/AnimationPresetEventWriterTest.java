package com.beatblock.timeline.generation;

import com.beatblock.BeatBlock;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editor.SelectionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creator lifecycle for Animation Library → Timeline drops:
 * one CompositeCommand undo, created ids, and post-insert selection.
 */
@WithBeatBlockContext
class AnimationPresetEventWriterTest {

	private Timeline timeline;
	private TimelineEditor editor;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(60.0);
		MusicPlayer musicPlayer = new MusicPlayer();
		editor = new TimelineEditor(timeline, musicPlayer);
		BeatBlock.installContext(BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build());
	}

	@Test
	void multiTargetDropIsOneUndoAndReturnsCreatedIds() {
		var result = AnimationPresetEventWriter.writePresetEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			"Pulse",
			12.5,
			List.of("a", "b", "c")
		);

		assertEquals(3, result.written());
		assertEquals(3, result.eventIds().size());
		assertEquals(3, result.clipIds().size());
		assertFalse(result.unbound());
		assertEquals(3, timeline.getBlockAnimationEvents().size());
		assertEquals(1, editor.getCommandManager().undoCount());

		editor.getCommandManager().undo();
		assertEquals(0, timeline.getBlockAnimationEvents().size());
		assertEquals(0, editor.getCommandManager().undoCount());
	}

	@Test
	void selectCreatedEventsSelectsAllForBatchEdit() {
		var result = AnimationPresetEventWriter.writePresetEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			"BlockTap",
			20.0,
			List.of("t1", "t2")
		);
		SelectionState selection = editor.getSelectionState();
		selection.selectEvent("stale-old-id");
		selection.selectClip("stale-clip");

		AnimationPresetEventWriter.selectCreatedEvents(selection, result);

		assertEquals(2, selection.getSelectedEvents().size());
		assertTrue(selection.getSelectedEvents().containsAll(result.eventIds()));
		assertTrue(selection.getSelectedClips().isEmpty());
		assertEquals(result.eventIds().getFirst(), selection.getRangeAnchorEventId());
	}

	@Test
	void unboundDropMarksResultAndStillCreatesEvent() {
		var result = AnimationPresetEventWriter.writePresetEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			"Pulse",
			1.0,
			List.of("")
		);

		assertEquals(1, result.written());
		assertTrue(result.unbound());
		assertEquals("", timeline.getBlockAnimationEvents().getFirst().getTargetObjectId());
	}

	@Test
	void missingPresetReturnsEmpty() {
		var result = AnimationPresetEventWriter.writePresetEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			"DefinitelyNotAPreset",
			1.0,
			List.of("stage")
		);
		assertEquals(0, result.written());
		assertTrue(result.eventIds().isEmpty());
	}
}
