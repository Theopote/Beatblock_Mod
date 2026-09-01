package com.beatblock.client.export;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.TimelineCompiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/** 视频导出同步回归用的确定性演出工程。 */
final class VideoExportSyncFixtures {

	private VideoExportSyncFixtures() {}

	static CompiledTimelineSnapshot tenSecondShowcase() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(20.0);
		timeline.setMetadata("audioPath", "golden://audio/export-sync.wav");
		timeline.setMetadata("bpm", 120.0);

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"place-tower", 2.0, 1.0, "Pulse", "stage-main", 1f,
			Map.of(
				"actionMode", "PLACE",
				"playbackSemantics", "STATEFUL",
				"animationType", "Pulse",
				"targetObject", "stage-main",
				"durationSeconds", 1.0)));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"build-wall", 5.0, 1.0, "Pulse", "stage-main", 1f,
			Map.of(
				"actionMode", "BUILD",
				"playbackSemantics", "STATEFUL",
				"animationType", "Pulse",
				"targetObject", "stage-main",
				"durationSeconds", 1.0)));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"pulse-hit", 8.0, 0.5, "Pulse", "stage-main", 1f,
			Map.of(
				"actionMode", "ANIMATE",
				"playbackSemantics", "TRANSIENT",
				"animationType", "Pulse",
				"targetObject", "stage-main",
				"durationSeconds", 0.5)));

		var cameraTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		var cameraClip = TimelineOperations.addClip(cameraTrack, 0.0, 20.0);
		TimelineOperations.addEvent(cameraClip, 0.0, EventType.CAMERA_KEYFRAME, Map.of(
			"x", 0.0, "y", 80.0, "z", 30.0, "yawDeg", 0.0, "pitchDeg", -20.0));
		TimelineOperations.addEvent(cameraClip, 20.0, EventType.CAMERA_KEYFRAME, Map.of(
			"x", 20.0, "y", 70.0, "z", 10.0, "yawDeg", 45.0, "pitchDeg", -15.0));

		var globalTrack = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		var globalClip = TimelineOperations.addClip(globalTrack, 0.0, 20.0);
		TimelineOperations.addEvent(globalClip, 8.0, EventType.GLOBAL, Map.of(
			"type", "screen_tint", "name", "Chorus Tint", "intensity", 0.4,
			"r", 0.1, "g", 0.2, "b", 1.0, "durationSeconds", 5.0));
		TimelineOperations.addEvent(globalClip, 9.5, EventType.GLOBAL, Map.of(
			"type", "screen-flash", "name", "Pre-Drop Flash", "durationSeconds", 1.0,
			"r", 1.0, "g", 1.0, "b", 1.0, "intensity", 0.9));

		BlockAnimationEngine engine = new BlockAnimationEngine();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage-main", "Main Stage", java.util.List.of(new BlockPos(0, 64, 0))));

		return TimelineCompiler.compile(timeline, engine, null);
	}

	static Vec3d cameraAnchor() {
		return new Vec3d(0.0, 64.0, 0.0);
	}
}
