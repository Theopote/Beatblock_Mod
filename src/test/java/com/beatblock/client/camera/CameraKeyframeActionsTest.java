package com.beatblock.client.camera;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.camera.CameraTrackFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraKeyframeActionsTest {

	@Test
	void deleteKeyframeEventRemovesMatchingEvent() {
		Timeline timeline = Timeline.createDefault();
		CameraTrackFactory.addPathSegment(timeline, 0, 0, 64, 0, 0, 0);
		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		var keyframe = TimelineOperations.addEvent(
			clip, 2.0, EventType.CAMERA_KEYFRAME, Map.of("x", 0.0));

		assertTrue(CameraKeyframeActions.deleteKeyframeEvent(timeline, keyframe.getId()));
		assertFalse(CameraKeyframeActions.deleteKeyframeEvent(timeline, "missing"));
	}

	@Test
	void deleteKeyframeEventReturnsFalseWhenNoCameraTrack() {
		Timeline timeline = new Timeline();
		assertFalse(CameraKeyframeActions.deleteKeyframeEvent(timeline, "evt-1"));
	}
}
