package com.beatblock.timeline.editing;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.interaction.DragController;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEventMovePolicyTest {

	private static TimelineToolbarState snapDisabled() {
		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setSnapToGrid(false);
		toolbar.setSnapToBeat(false);
		toolbar.setMagnetSnap(false);
		return toolbar;
	}

	@Test
	void cameraKeyframeAndGlobalClampToClipRange() {
		Clip clip = new Clip("c", 10.0, 20.0);
		TimelineEvent keyframe = new TimelineEvent("k", 15.0, EventType.CAMERA_KEYFRAME, Map.of());
		TimelineEvent global = new TimelineEvent("g", 12.0, EventType.GLOBAL, Map.of());

		var kfBounds = TimelineEventMovePolicy.boundsFor(clip, keyframe);
		assertEquals(10.0, kfBounds.minTimeSeconds(), 1e-9);
		assertEquals(20.0, kfBounds.maxTimeSeconds(), 1e-9);
		assertEquals(20.0, kfBounds.clamp(40.0), 1e-9);
		assertEquals(10.0, kfBounds.clamp(5.0), 1e-9);

		var globalBounds = TimelineEventMovePolicy.boundsFor(clip, global);
		assertEquals(10.0, globalBounds.minTimeSeconds(), 1e-9);
		assertEquals(20.0, globalBounds.maxTimeSeconds(), 1e-9);
	}

	@Test
	void animationEventAlsoClampsToClipNotTimelineDuration() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(60.0);
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 10.0, 20.0);
		TimelineEvent event = TimelineOperations.addEvent(clip, 15.0, EventType.ANIMATION, Map.of());

		DragController.dragEvent(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), event.getId(),
			40.0, snapDisabled(), null, null);

		assertEquals(20.0, event.getTimeSeconds(), 1e-9);
		assertEquals(10.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(20.0, clip.getEndTimeSeconds(), 1e-9);
	}

	@Test
	void cameraSegmentHeadIsNotFreelyMovable() {
		Clip clip = new Clip("c", 10.0, 20.0);
		TimelineEvent segment = new TimelineEvent("s", 10.0, EventType.CAMERA_SEGMENT, Map.of("kind", "PATH"));
		clip.addEvent(segment);

		var bounds = TimelineEventMovePolicy.boundsFor(clip, segment);
		assertTrue(bounds.isFixed());
		assertEquals(10.0, bounds.clamp(18.0), 1e-9);

		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		track.addClip(clip);
		DragController.dragEvent(
			timeline, Timeline.TRACK_ID_CAMERA, clip.getId(), segment.getId(),
			18.0, snapDisabled(), null, null);
		assertEquals(10.0, segment.getTimeSeconds(), 1e-9);
	}
}
