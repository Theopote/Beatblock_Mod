package com.beatblock.timeline.project;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.GlobalEvent;
import com.beatblock.timeline.GlobalEventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.Track;
import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineAnimationPersistenceTest {

	@Test
	void roundTripsAutoAndBlockAnimationTracks() {
		Timeline original = Timeline.createDefault();
		original.addAutoAnimationEvent(new TimelineAnimationEvent(
			"auto-1", 2.0, 1.0, "build", "stage-a", 0.8f,
			Map.of("eventOrigin", TimelineEventOrigin.GENERATED.name(), "buildMode", "wall")));
		original.addBlockAnimationEvent(new TimelineAnimationEvent(
			"block-1", 5.0, 0.5, "pulse", "stage-b", 0.6f,
			Map.of("eventOrigin", TimelineEventOrigin.MANUAL.name())));

		JsonArray json = TimelineAnimationPersistence.toJson(original);
		assertEquals(2, json.size());

		Timeline restored = Timeline.createDefault();
		TimelineAnimationPersistence.loadInto(restored, json);

		assertEquals(1, restored.getAutoAnimationEvents().size());
		assertEquals(1, restored.getBlockAnimationEvents().size());
		assertEquals("stage-a", restored.getAutoAnimationEvents().getFirst().getTargetObjectId());
		assertEquals("wall", restored.getAutoAnimationEvents().getFirst().getParameters().get("buildMode"));
		assertEquals("stage-b", restored.getBlockAnimationEvents().getFirst().getTargetObjectId());
		assertEquals(TimelineEventOrigin.MANUAL, restored.getBlockAnimationEvents().getFirst().getEventOrigin());
	}

	@Test
	void loadIntoClearsExistingAnimationTracksFirst() {
		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"old", 0.0, 1.0, "build", "stage", 1f, Map.of()));

		Timeline restoredSource = Timeline.createDefault();
		restoredSource.addAutoAnimationEvent(new TimelineAnimationEvent(
			"new", 3.0, 1.0, "pulse", "stage", 1f, Map.of()));
		JsonArray json = TimelineAnimationPersistence.toJson(restoredSource);

		TimelineAnimationPersistence.loadInto(timeline, json);

		assertEquals(1, timeline.getAutoAnimationEvents().size());
		assertEquals(3.0, timeline.getAutoAnimationEvents().getFirst().getTimeSeconds(), 1e-9);
		assertTrue(timeline.getBlockAnimationEvents().isEmpty());
	}

	@Test
	void roundTripsCameraAndGlobalTracks() {
		Timeline original = Timeline.createDefault();
		original.addCameraKeyframe(new com.beatblock.timeline.CameraKeyframe(4.5));
		original.addGlobalEvent(new GlobalEvent(6.0, GlobalEventType.SPECIAL, "Drop FX"));

		JsonArray json = TimelineAnimationPersistence.toJson(original);

		Timeline restored = Timeline.createDefault();
		TimelineAnimationPersistence.loadInto(restored, json);

		assertEquals(1, restored.getCameraKeyframes().size());
		assertEquals(4.5, restored.getCameraKeyframes().getFirst().getTimeSeconds(), 1e-9);
		assertEquals(1, restored.getGlobalEvents().size());
		assertEquals("Drop FX", restored.getGlobalEvents().getFirst().getName());
		assertEquals(GlobalEventType.SPECIAL, restored.getGlobalEvents().getFirst().getType());
	}

	@Test
	void roundTripsBlockAnimationFeatureTrack() {
		Timeline original = Timeline.createDefault();
		String featureTrackId = Timeline.blockAnimationFeatureTrackId("kick");
		original.addTrack(new com.beatblock.timeline.Track(featureTrackId, "kick", com.beatblock.timeline.TrackType.ANIMATION));
		original.addAnimationEvent(featureTrackId, new TimelineAnimationEvent(
			"feat-1", 1.0, 0.5, "pulse", "stage", 1f, Map.of()));

		JsonArray json = TimelineAnimationPersistence.toJson(original);
		assertEquals(1, json.size());

		Timeline restored = Timeline.createDefault();
		TimelineAnimationPersistence.loadInto(restored, json);

		assertNotNull(restored.getTrack(featureTrackId));
		assertEquals(1, restored.getAnimationEvents(featureTrackId).size());
		assertEquals("stage", restored.getAnimationEvents(featureTrackId).getFirst().getTargetObjectId());
	}

	@Test
	void roundTripsAudioTrackClips() {
		Timeline original = Timeline.createDefault();
		Track audio = original.getTrack(Timeline.TRACK_ID_AUDIO);
		assertNotNull(audio);
		Clip clip = new Clip("audio-clip-1", 1.5, 8.0);
		audio.addClip(clip);

		JsonArray json = TimelineAnimationPersistence.toJson(original);
		assertEquals(1, json.size());
		assertEquals(Timeline.TRACK_ID_AUDIO, json.get(0).getAsJsonObject().get("trackId").getAsString());

		Timeline restored = Timeline.createDefault();
		restored.getTrack(Timeline.TRACK_ID_AUDIO).addClip(new Clip("stale", 0.0, 1.0));
		TimelineAnimationPersistence.loadInto(restored, json);

		Track restoredAudio = restored.getTrack(Timeline.TRACK_ID_AUDIO);
		assertNotNull(restoredAudio);
		assertEquals(1, restoredAudio.getClips().size());
		Clip restoredClip = restoredAudio.getClips().getFirst();
		assertEquals("audio-clip-1", restoredClip.getId());
		assertEquals(1.5, restoredClip.getStartTimeSeconds(), 1e-9);
		assertEquals(8.0, restoredClip.getEndTimeSeconds(), 1e-9);
	}
}
