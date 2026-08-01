package com.beatblock.client.camera;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.playback.TimelineCompiler;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimelineCameraCompiledPlaybackTest {

	@Test
	void compiledCameraEvaluationDoesNotObserveDocumentMutation() {
		Timeline timeline = Timeline.createDefault();
		var clip = TimelineOperations.addClip(timeline.getTrack(Timeline.TRACK_ID_CAMERA), 0.0, 2.0);
		var keyframe = TimelineOperations.addEvent(clip, 0.0, EventType.CAMERA_KEYFRAME,
			Map.of("x", 4.0, "y", 5.0, "z", 6.0, "yawDeg", 30.0, "pitchDeg", 10.0));
		var snapshot = TimelineCompiler.compile(timeline);

		keyframe.setParameter("x", 100.0);
		var sample = TimelineCameraEvaluator.evaluate(
			snapshot.cameraTrack(), snapshot.bpm(), 1.0, Vec3d.ZERO, 0f, 0f);

		assertEquals(4.0, sample.position().x, 1e-9);
		assertEquals(30.0f, sample.yawDeg(), 1e-6f);
	}

	@Test
	void liveTimelineEvaluateSeesMutationButCompiledDoesNot() {
		Timeline timeline = Timeline.createDefault();
		var clip = TimelineOperations.addClip(timeline.getTrack(Timeline.TRACK_ID_CAMERA), 0.0, 2.0);
		var keyframe = TimelineOperations.addEvent(clip, 0.0, EventType.CAMERA_KEYFRAME,
			Map.of("x", 1.0, "y", 2.0, "z", 3.0, "yawDeg", 0.0, "pitchDeg", 0.0));
		var snapshot = TimelineCompiler.compile(timeline);

		keyframe.setParameter("x", 50.0);

		var live = TimelineCameraEvaluator.evaluate(timeline, 1.0, Vec3d.ZERO, 0f, 0f);
		var frozen = TimelineCameraEvaluator.evaluate(
			snapshot.cameraTrack(), snapshot.bpm(), 1.0, Vec3d.ZERO, 0f, 0f);

		assertEquals(50.0, live.position().x, 1e-9);
		assertEquals(1.0, frozen.position().x, 1e-9);
	}
}
