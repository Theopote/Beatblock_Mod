package com.beatblock.timeline.generation;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.command.CommandManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@WithBeatBlockContext
class TimelineDraftWriterTest {

	@Test
	void withOriginTagsEventParameters() {
		var source = new TimelineAnimationEvent(
			"ev1", 1.0, 0.5, "build", "stage", 0.8f, Map.of("buildMode", "wall"));
		var tagged = TimelineDraftWriter.withOrigin(source, TimelineEventOrigin.MANUAL);

		assertEquals("MANUAL", tagged.getParameters().get("eventOrigin"));
		assertEquals("wall", tagged.getParameters().get("buildMode"));
		assertNull(TimelineDraftWriter.withOrigin(null, TimelineEventOrigin.MANUAL));
	}

	@Test
	void insertGeneratedEventsAddsToTimeline() {
		Timeline timeline = Timeline.createDefault();
		var events = List.of(
			new TimelineAnimationEvent("ev1", 2.0, 1.0, "build", "stage", 1f, Map.of()),
			new TimelineAnimationEvent("ev2", 4.0, 0.5, "pulse", "stage", 0.5f, Map.of())
		);

		int written = TimelineDraftWriter.insertGeneratedEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_AUTO,
			events
		);

		assertEquals(2, written);
		assertEquals(2, timeline.getAutoAnimationEvents().size());
		assertEquals("GENERATED", timeline.getAutoAnimationEvents().getFirst().getParameters().get("eventOrigin"));
	}

	@Test
	void replaceGeneratedEventsClearsAutoTrackThenInserts() {
		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"old", 0.0, 1.0, "build", "stage", 1f, Map.of()));

		int written = TimelineDraftWriter.replaceGeneratedEvents(
			timeline,
			List.of(new TimelineAnimationEvent("new", 1.0, 1.0, "build", "stage", 1f, Map.of()))
		);

		assertEquals(1, written);
		assertEquals(1, timeline.getAutoAnimationEvents().size());
		assertEquals(1.0, timeline.getAutoAnimationEvents().getFirst().getTimeSeconds(), 1e-9);
		assertEquals("stage", timeline.getAutoAnimationEvents().getFirst().getTargetObjectId());
	}

	@Test
	void insertManualEventsBatchesAsOneUndoEntry() {
		Timeline timeline = Timeline.createDefault();
		MusicPlayer musicPlayer = new MusicPlayer();
		TimelineEditor editor = new TimelineEditor(timeline, musicPlayer);
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build();
		com.beatblock.BeatBlock.installContext(context);

		var events = List.of(
			new TimelineAnimationEvent("ev1", 2.0, 0.5, "Pulse", "a", 1f, Map.of()),
			new TimelineAnimationEvent("ev2", 2.0, 0.5, "Pulse", "b", 1f, Map.of()),
			new TimelineAnimationEvent("ev3", 2.0, 0.5, "Pulse", "c", 1f, Map.of())
		);

		int written = TimelineDraftWriter.insertManualEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			events
		);

		assertEquals(3, written);
		assertEquals(3, timeline.getBlockAnimationEvents().size());
		assertEquals(1, editor.getCommandManager().undoCount());

		editor.getCommandManager().undo();
		assertEquals(0, timeline.getBlockAnimationEvents().size());
		assertEquals(0, editor.getCommandManager().undoCount());
	}

	@Test
	void multiTargetPresetDropIsOneUndo() {
		Timeline timeline = Timeline.createDefault();
		MusicPlayer musicPlayer = new MusicPlayer();
		TimelineEditor editor = new TimelineEditor(timeline, musicPlayer);
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build();
		com.beatblock.BeatBlock.installContext(context);

		var result = AnimationPresetEventWriter.writePresetEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			"Pulse",
			10.0,
			List.of("t1", "t2", "t3", "t4", "t5")
		);

		assertEquals(5, result.written());
		assertEquals(5, timeline.getBlockAnimationEvents().size());
		assertEquals(1, editor.getCommandManager().undoCount());

		editor.getCommandManager().undo();
		assertEquals(0, timeline.getBlockAnimationEvents().size());
	}

	@Test
	void replaceGeneratedEventsIsOneUndoIncludingClear() {
		Timeline timeline = Timeline.createDefault();
		MusicPlayer musicPlayer = new MusicPlayer();
		TimelineEditor editor = new TimelineEditor(timeline, musicPlayer);
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build();
		com.beatblock.BeatBlock.installContext(context);

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"old", 0.0, 1.0, "build", "stage", 1f, Map.of()));
		assertEquals(1, timeline.getAutoAnimationEvents().size());

		int written = TimelineDraftWriter.replaceGeneratedEvents(
			timeline,
			List.of(
				new TimelineAnimationEvent("n1", 1.0, 1.0, "build", "a", 1f, Map.of()),
				new TimelineAnimationEvent("n2", 2.0, 1.0, "build", "b", 1f, Map.of())
			)
		);

		assertEquals(2, written);
		assertEquals(2, timeline.getAutoAnimationEvents().size());
		assertEquals(1, editor.getCommandManager().undoCount());

		editor.getCommandManager().undo();
		assertEquals(1, timeline.getAutoAnimationEvents().size());
		assertEquals(0.0, timeline.getAutoAnimationEvents().getFirst().getTimeSeconds(), 1e-9);
	}

	@Test
	void commandManagerOrNullReadsFromInjectedContext() {
		Timeline timeline = Timeline.createDefault();
		MusicPlayer musicPlayer = new MusicPlayer();
		TimelineEditor editor = new TimelineEditor(timeline, musicPlayer);
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.build();

		CommandManager commands = TimelineDraftWriter.commandManagerOrNull(context);

		assertSame(editor.getCommandManager(), commands);
	}
}
