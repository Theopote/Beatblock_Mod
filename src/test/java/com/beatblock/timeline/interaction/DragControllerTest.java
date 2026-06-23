package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DragControllerTest {

	private static TimelineToolbarState snapDisabled() {
		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setSnapToGrid(false);
		toolbar.setSnapToBeat(false);
		toolbar.setMagnetSnap(false);
		return toolbar;
	}

	@Test
	void dragEventClampsTimeToDuration() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 5);
		TimelineEvent event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());

		DragController.dragEvent(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), event.getId(),
			12.0, 8.0, snapDisabled(), null, null);

		assertEquals(8.0, event.getTimeSeconds(), 1e-9);
	}

	@Test
	void dragClipMovesByMouseDelta() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 2.0, 5.0);

		double newStart = DragController.dragClip(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(),
			4.0, 3.0, 2.0, 3.0, 60.0, snapDisabled(), null, null);

		assertEquals(3.0, newStart, 1e-9);
		assertEquals(3.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(6.0, clip.getEndTimeSeconds(), 1e-9);
	}

	@Test
	void snapTimeReturnsUnchangedWhenAllSnapsDisabled() {
		Timeline timeline = Timeline.createDefault();
		double time = DragController.snapTime(2.37, null, timeline, snapDisabled(), null);
		assertEquals(2.37, time, 1e-9);
	}

	@Test
	void dragEventStoresAlignmentGuidesInInteractionState() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("bpm", 120.0);
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 4);
		TimelineEvent event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());
		InteractionState state = new InteractionState();

		DragController.dragEvent(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), event.getId(),
			1.02, 10.0, new TimelineToolbarState(), null, state);

		assertEquals(1.0, event.getTimeSeconds(), 1e-9);
	}
}
