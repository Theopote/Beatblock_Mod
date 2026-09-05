package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editing.AnimationEventSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateClipPropertiesCommandTest {

	@Test
	void executeAppliesClipTimingWithoutEventsAndUndoRestores() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		var clip = TimelineOperations.addClip(track, 1.0, 6.0);
		assertTrue(clip.getEvents().isEmpty());

		String labelKey = "clipLabel_" + clip.getId();
		timeline.setMetadata(labelKey, "Intro");

		AnimationEventSnapshot before = new AnimationEventSnapshot(
			0.0, Map.of(), 1.0, 6.0, Map.of(), Map.of(labelKey, "Intro"), timeline.getDurationSeconds()
		);
		AnimationEventSnapshot after = new AnimationEventSnapshot(
			0.0, Map.of(), 5.0, 20.0, Map.of(), Map.of(labelKey, "Verse"), Math.max(timeline.getDurationSeconds(), 20.0)
		);

		UpdateClipPropertiesCommand command = new UpdateClipPropertiesCommand(
			timeline, track.getId(), clip.getId(), before, after);
		command.execute();

		assertEquals(5.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(20.0, clip.getEndTimeSeconds(), 1e-9);
		assertEquals("Verse", String.valueOf(timeline.getMetadata(labelKey)));

		command.undo();
		assertEquals(1.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(6.0, clip.getEndTimeSeconds(), 1e-9);
		assertEquals("Intro", String.valueOf(timeline.getMetadata(labelKey)));
	}

	@Test
	void canMergeWithSameClipWithinWindow() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		AnimationEventSnapshot a = new AnimationEventSnapshot(0, Map.of(), 0, 4);
		AnimationEventSnapshot b = new AnimationEventSnapshot(0, Map.of(), 1, 5);
		AnimationEventSnapshot c = new AnimationEventSnapshot(0, Map.of(), 2, 6);

		var first = new UpdateClipPropertiesCommand(timeline, track.getId(), clip.getId(), a, b);
		var second = new UpdateClipPropertiesCommand(timeline, track.getId(), clip.getId(), b, c);
		assertTrue(first.canMergeWith(second));
		UpdateClipPropertiesCommand merged = (UpdateClipPropertiesCommand) first.mergeWith(second);
		merged.execute();
		assertEquals(2.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(6.0, clip.getEndTimeSeconds(), 1e-9);
	}
}
