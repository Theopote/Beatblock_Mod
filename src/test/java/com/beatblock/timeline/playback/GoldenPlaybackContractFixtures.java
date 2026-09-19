package com.beatblock.timeline.playback;

import com.beatblock.client.export.VideoExportFrameSampler;
import com.beatblock.client.export.VideoExportFrameState;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.video.VideoExportSettings;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Golden Playback Contract 固定工程：2048 blocks、跨 chunk、BUILD/PLACE/CLEAR + Camera/VFX。
 */
public final class GoldenPlaybackContractFixtures {

	public static final int BLOCK_COUNT = 2048;
	public static final double BUILD_START = 5.0;
	public static final double BUILD_DURATION = 10.0;
	public static final double BUILD_END = BUILD_START + BUILD_DURATION;
	public static final double PLACE_AT = 18.0;
	public static final double CLEAR_AT = 24.0;
	public static final double PROBE = 12.0;
	public static final double LOOP_OUT = 20.0;
	public static final double DURATION = 30.0;
	public static final int EXPORT_FPS = 60;
	public static final int EXPORT_FRAME_AT_PROBE = (int) Math.round(PROBE * EXPORT_FPS);

	public static final String STAGE_ID = "golden-stage";

	private GoldenPlaybackContractFixtures() {}

	public static CompiledTimelineSnapshot program() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(DURATION);
		timeline.setMetadata("audioPath", "golden://audio/playback-contract.wav");
		timeline.setMetadata("bpm", 120.0);

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"build-mega", BUILD_START, BUILD_DURATION, "Pulse", STAGE_ID, 1f,
			Map.of(
				"actionMode", "BUILD",
				"playbackSemantics", "STATEFUL",
				"animationType", "Pulse",
				"targetObject", STAGE_ID,
				"buildMode", "wall",
				"durationSeconds", BUILD_DURATION)));

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"place-mark", PLACE_AT, 0.5, "Pulse", STAGE_ID, 1f,
			Map.of(
				"actionMode", "PLACE",
				"playbackSemantics", "STATEFUL",
				"animationType", "Pulse",
				"targetObject", STAGE_ID,
				"durationSeconds", 0.5)));

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"clear-mark", CLEAR_AT, 0.5, "Pulse", STAGE_ID, 1f,
			Map.of(
				"actionMode", "CLEAR",
				"playbackSemantics", "STATEFUL",
				"animationType", "Pulse",
				"targetObject", STAGE_ID,
				"durationSeconds", 0.5)));

		var cameraTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		var cameraClip = TimelineOperations.addClip(cameraTrack, 0.0, DURATION);
		TimelineOperations.addEvent(cameraClip, 0.0, EventType.CAMERA_KEYFRAME, Map.of(
			"x", 0.0, "y", 80.0, "z", 40.0, "yawDeg", 0.0, "pitchDeg", -25.0));
		TimelineOperations.addEvent(cameraClip, DURATION, EventType.CAMERA_KEYFRAME, Map.of(
			"x", 64.0, "y", 72.0, "z", 8.0, "yawDeg", 90.0, "pitchDeg", -10.0));

		var globalTrack = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		var globalClip = TimelineOperations.addClip(globalTrack, 0.0, DURATION);
		TimelineOperations.addEvent(globalClip, 10.0, EventType.GLOBAL,
			GlobalEventPayloadCodec.encode(
				new GlobalEventPayload.ScreenTint("Golden Tint", 0.35, 0.2f, 0.4f, 0.9f, 8.0)));
		TimelineOperations.addEvent(globalClip, 11.0, EventType.GLOBAL,
			GlobalEventPayloadCodec.encode(
				new GlobalEventPayload.ScreenFlash("Golden Flash", 1.0f, 0.9f, 0.8f, 1.5)));

		// 跨 chunk：沿 X 轴铺开（2048 → 128 chunks）
		List<BlockPos> blocks = new ArrayList<>(BLOCK_COUNT);
		for (int i = 0; i < BLOCK_COUNT; i++) {
			blocks.add(new BlockPos(i, 64, (i / 16) % 32));
		}

		BlockAnimationEngine engine = new BlockAnimationEngine();
		engine.getStageObjectSystem().register(
			StageObjectSystem.fromBlocks(STAGE_ID, "Golden Stage", blocks));

		// 不注入分析节拍：BUILD 使用线性时间戳，确保 probe=12s 的进度可预测（约 70%）。
		return TimelineCompiler.compile(timeline, engine, null);
	}

	public static Vec3d cameraAnchor() {
		return new Vec3d(0.0, 64.0, 0.0);
	}

	public static VideoExportSettings exportSettings(Path output) {
		return new VideoExportSettings(output, 1280, 720, EXPORT_FPS, 0.0, DURATION, true);
	}

	public static WorldStateFingerprint exportProbe(
		CompiledTimelineSnapshot compiled,
		Path output
	) {
		VideoExportFrameState frame = VideoExportFrameSampler.sample(
			compiled,
			exportSettings(output),
			EXPORT_FRAME_AT_PROBE,
			cameraAnchor(),
			0f,
			0f,
			VideoExportFrameSampler.DEFAULT_AUDIO_SAMPLE_RATE
		);
		assert Math.abs(frame.timelineTimeSeconds() - PROBE) < 1e-9
			: "export frame time must be probe: " + frame.timelineTimeSeconds();
		return WorldStateFingerprint.fromExportFrame(
			compiled, frame.stageState(), frame.timelineTimeSeconds(), cameraAnchor());
	}
}
