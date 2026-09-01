package com.beatblock.timeline.project.golden;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.GlobalEvent;
import com.beatblock.timeline.GlobalEventType;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.automap.camera.CameraSegmentSemantics;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 官方 Golden Project 的程序化定义（用于生成 {@code src/test/resources/projects/*.osc}）。 */
public final class GoldenProjectFixtures {

	public static final String AUDIO_PLACEHOLDER = "golden://audio/placeholder.wav";

	private GoldenProjectFixtures() {}

	public static GoldenProjectContext minimal() {
		Timeline timeline = baseTimeline("Minimal Golden", "golden-minimal", 60.0, 120.0);
		timeline.addMarker(new TimelineMarker("intro", 0.0, "Intro", MarkerType.GENERIC));
		return GoldenProjectContext.of(timeline, emptyLayers());
	}

	public static GoldenProjectContext buildDemo() {
		Timeline timeline = baseTimeline("Build Demo", "golden-build-demo", 45.0, 128.0);
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());

		RuntimeStageObject tower = StageObjectSystem.fromBlocks("stage-tower", "Tower",
			List.of(new BlockPos(0, 64, 0), new BlockPos(0, 65, 0), new BlockPos(0, 66, 0)));
		layers.registerRestored(new BuildLayer(
			"layer-tower", "Tower", tower, LayerVisibilityState.FREE_HIDDEN,
			Map.of(new BlockPos(0, 64, 0), Blocks.STONE_BRICKS.getDefaultState()), null));

		RuntimeStageObject arch = StageObjectSystem.fromBlocks("stage-arch", "Arch",
			List.of(new BlockPos(4, 64, 0), new BlockPos(5, 64, 0), new BlockPos(4, 65, 0)));
		layers.registerRestored(new BuildLayer(
			"layer-arch", "Arch", arch, LayerVisibilityState.FREE_HIDDEN,
			Map.of(new BlockPos(4, 64, 0), Blocks.QUARTZ_BLOCK.getDefaultState()), null));

		bindBuildClip(timeline, layers, "layer-tower", tower.getId(), "clip-tower", "evt-tower", 4.0, 18.0);
		bindBuildClip(timeline, layers, "layer-arch", arch.getId(), "clip-arch", "evt-arch", 20.0, 36.0);

		timeline.addMarker(new TimelineMarker("build-a", 4.0, "Tower Build", MarkerType.SECTION));
		timeline.addMarker(new TimelineMarker("build-b", 20.0, "Arch Build", MarkerType.SECTION));
		return GoldenProjectContext.of(timeline, layers);
	}

	public static GoldenProjectContext threeBand() {
		Timeline timeline = baseTimeline("Three Band", "golden-three-band", 32.0, 140.0);
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());
		RuntimeStageObject stage = StageObjectSystem.fromBlocks("stage-band", "Band Target",
			List.of(new BlockPos(2, 64, 2)));
		layers.registerRestored(new BuildLayer(
			"layer-band", "Band Target", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		addFeatureBand(timeline, "kick", "kick-1", 0.0, stage.getId());
		addFeatureBand(timeline, "bass", "bass-1", 8.0, stage.getId());
		addFeatureBand(timeline, "hihat", "hihat-1", 16.0, stage.getId());
		timeline.addMarker(new TimelineMarker("drop", 24.0, "Drop", MarkerType.DROP));
		return GoldenProjectContext.of(timeline, layers);
	}

	public static GoldenProjectContext cameraVfx() {
		Timeline timeline = baseTimeline("Camera VFX", "golden-camera-vfx", 48.0, 128.0);
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());
		RuntimeStageObject stage = StageObjectSystem.fromBlocks("stage-vfx", "VFX Stage",
			List.of(new BlockPos(0, 64, 0)));
		layers.registerRestored(new BuildLayer(
			"layer-vfx", "VFX Stage", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		timeline.addAutoAnimationEvent(stageEvent("pulse-intro", 2.0, "Pulse", stage.getId()));

		var cameraTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		var cameraClip = TimelineOperations.addClip(cameraTrack, 0.0, 48.0);
		TimelineOperations.addEvent(cameraClip, 0.0, EventType.CAMERA_KEYFRAME, Map.of(
			"x", 0.0, "y", 72.0, "z", 24.0, "yawDeg", 0.0, "pitchDeg", -25.0));
		TimelineOperations.addEvent(cameraClip, 16.0, EventType.CAMERA_KEYFRAME, Map.of(
			"x", 12.0, "y", 66.0, "z", 8.0, "yawDeg", 55.0, "pitchDeg", -12.0));
		TimelineOperations.addEvent(cameraClip, 32.0, EventType.CAMERA_KEYFRAME, Map.of(
			"x", -6.0, "y", 70.0, "z", 18.0, "yawDeg", -30.0, "pitchDeg", -18.0));

		var globalTrack = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		var globalClip = TimelineOperations.addClip(globalTrack, 0.0, 48.0);
		TimelineOperations.addEvent(globalClip, 6.0, EventType.GLOBAL, Map.of(
			"type", "LIGHTING", "name", "Verse Key", "intensity", 0.65,
			"r", 1.0, "g", 0.85, "b", 0.6, "durationSeconds", 4.0));
		TimelineOperations.addEvent(globalClip, 12.0, EventType.GLOBAL, Map.of(
			"type", "screen-flash", "name", "Hit Flash", "durationSeconds", 0.25,
			"r", 1.0, "g", 1.0, "b", 1.0, "intensity", 0.8));
		TimelineOperations.addEvent(globalClip, 20.0, EventType.GLOBAL, Map.of(
			"type", "WEATHER", "name", "Rain Mood", "weatherType", "rain", "durationSeconds", 6.0));
		TimelineOperations.addEvent(globalClip, 28.0, EventType.GLOBAL, Map.of(
			"type", "screen_tint", "name", "Chorus Tint", "intensity", 0.35,
			"r", 0.2, "g", 0.4, "b", 1.0, "durationSeconds", 8.0));
		return GoldenProjectContext.of(timeline, layers);
	}

	public static GoldenProjectContext manualPlusAutomap() {
		Timeline timeline = baseTimeline("Manual Plus Auto Map", "golden-manual-plus-automap", 48.0, 128.0);
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());
		RuntimeStageObject stage = StageObjectSystem.fromBlocks("stage-main", "Main Stage",
			List.of(new BlockPos(0, 64, 0)));
		layers.registerRestored(new BuildLayer(
			"layer-main", "Main Stage", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		addFeatureBand(timeline, "kick", "kick-1", 1.0, stage.getId());
		addFeatureBand(timeline, "bass", "bass-1", 8.0, stage.getId());
		addFeatureBand(timeline, "hihat", "hihat-1", 16.0, stage.getId());

		timeline.addCameraKeyframe(new com.beatblock.timeline.CameraKeyframe(4.0));
		timeline.addCameraKeyframe(new com.beatblock.timeline.CameraKeyframe(20.0));

		timeline.addGlobalEvent(new GlobalEvent(8.0, GlobalEventType.SCREEN_TINT, "Manual Tint A"));
		timeline.addGlobalEvent(new GlobalEvent(28.0, GlobalEventType.SPECIAL, "Manual Flash B"));

		timeline.addMarker(new TimelineMarker("intro", 0.0, "Intro", MarkerType.SECTION));
		timeline.addMarker(new TimelineMarker("drop", 24.0, "Drop", MarkerType.DROP));
		return GoldenProjectContext.of(timeline, layers);
	}

	public static GoldenProjectContext brokenReference() {
		Timeline timeline = baseTimeline("Broken Reference", "golden-broken-reference", 32.0, 120.0);
		timeline.setMetadata("audioPath", "");
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"missing-stage", 2.0, 1.0, "bounce", "missing-stage", 0.8f,
			Map.of(
				"animationType", "bounce",
				"targetObject", "missing-stage",
				"durationSeconds", 1.0,
				"actionMode", "ANIMATE")));

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"unknown-preset", 6.0, 1.0, "DefinitelyNotARealPreset", "also-missing-stage", 0.8f,
			Map.of(
				"animationType", "DefinitelyNotARealPreset",
				"targetObject", "also-missing-stage",
				"durationSeconds", 1.0,
				"actionMode", "ANIMATE")));

		Track buildTrack = BuildLayerTrackSupport.ensureDefaultTrack(timeline);
		Clip buildClip = new Clip("broken-build", 10.0, 14.0);
		buildTrack.addClip(buildClip);
		buildClip.addEvent(new TimelineEvent("broken-build-event", 10.0, EventType.ANIMATION, Map.of(
			"actionMode", "BUILD",
			"animationType", "Pulse",
			"targetObject", "missing-build-stage",
			"layerId", "missing-layer",
			"durationSeconds", 4.0,
			"energy", 1.0,
			"eventOrigin", TimelineEventOrigin.MANUAL.name(),
			"buildMode", "WALL",
			"buildDissolve", "false",
			"layerBound", "true")));
		timeline.markAnimationEventsDirty(buildTrack.getId());

		Map<String, Object> cameraSemantics = new HashMap<>();
		cameraSemantics.put(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_KIND, "STAGE_OBJECT");
		cameraSemantics.put(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_REF, "missing-camera-subject");
		CameraTrackFactory.addOrbitSegment(
			timeline, 18.0, 3.0, 0, 64, 0, 8, 3, 0, 90,
			TimelineEventOrigin.MANUAL, cameraSemantics);

		return GoldenProjectContext.of(timeline, layers);
	}

	public static GoldenProjectContext stress10k() {
		Timeline timeline = baseTimeline("Stress 10k", "golden-stress-10k", 10_000.0, 120.0);
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());
		RuntimeStageObject stage = StageObjectSystem.fromBlocks("stage-stress", "Stress Stage",
			List.of(new BlockPos(0, 64, 0)));
		layers.registerRestored(new BuildLayer(
			"layer-stress", "Stress Stage", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		for (int i = 0; i < 10_000; i++) {
			double time = i * (timeline.getDurationSeconds() / 10_000.0);
			timeline.addAutoAnimationEvent(stageEvent(
				"ev-" + i, time, "Pulse", stage.getId()));
		}
		return new GoldenProjectContext(timeline, layers, new double[] {0.0, 2500.0, 5000.0, 7500.0, 10_000.0});
	}

	private static void bindBuildClip(
		Timeline timeline,
		BuildLayerManager layers,
		String layerId,
		String stageId,
		String clipId,
		String eventId,
		double clipStart,
		double clipEnd
	) {
		Track buildTrack = BuildLayerTrackSupport.ensureDefaultTrack(timeline);
		Clip clip = new Clip(clipId, clipStart, clipEnd);
		buildTrack.addClip(clip);
		clip.addEvent(new TimelineEvent(eventId, clipStart, EventType.ANIMATION, Map.of(
			"actionMode", "BUILD",
			"animationType", "Pulse",
			"targetObject", stageId,
			"layerId", layerId,
			"durationSeconds", clipEnd - clipStart,
			"energy", 1.0,
			"eventOrigin", TimelineEventOrigin.MANUAL.name(),
			"buildMode", "WALL",
			"buildDissolve", "false",
			"layerBound", "true")));
		BuildLayer layer = layers.get(layerId);
		if (layer != null) {
			layers.bindToClip(layer, clipId);
		}
		timeline.markAnimationEventsDirty(buildTrack.getId());
	}

	private static Timeline baseTimeline(String name, String projectId, double durationSeconds, double bpm) {
		Timeline timeline = Timeline.createDefault();
		timeline.setName(name);
		timeline.setDurationSeconds(durationSeconds);
		timeline.setMetadata("projectId", projectId);
		timeline.setMetadata("audioPath", AUDIO_PLACEHOLDER);
		timeline.setMetadata("bpm", bpm);
		return timeline;
	}

	private static BuildLayerManager emptyLayers() {
		return new BuildLayerManager(new StageObjectSystem());
	}

	private static void addFeatureBand(Timeline timeline, String featureKey, String eventId, double timeSeconds, String stageId) {
		String trackId = Timeline.blockAnimationFeatureTrackId(featureKey);
		if (timeline.getTrack(trackId) == null) {
			timeline.addTrack(new Track(trackId, featureKey, TrackType.ANIMATION));
		}
		timeline.addAnimationEvent(trackId, stageEvent(eventId, timeSeconds, "BlockTap", stageId));
	}

	private static TimelineAnimationEvent stageEvent(String id, double timeSeconds, String animationType, String stageId) {
		return new TimelineAnimationEvent(
			id, timeSeconds, 1.0, animationType, stageId, 0.85f,
			Map.of(
				"animationType", animationType,
				"targetObject", stageId,
				"durationSeconds", 1.0,
				"actionMode", "ANIMATE",
				"playbackSemantics", "TRANSIENT"));
	}

	public static List<Map.Entry<String, GoldenProjectContext>> all() {
		List<Map.Entry<String, GoldenProjectContext>> projects = new ArrayList<>();
		projects.add(Map.entry("minimal.osc", minimal()));
		projects.add(Map.entry("build-demo.osc", buildDemo()));
		projects.add(Map.entry("three-band.osc", threeBand()));
		projects.add(Map.entry("camera-vfx.osc", cameraVfx()));
		projects.add(Map.entry("manual-plus-automap.osc", manualPlusAutomap()));
		projects.add(Map.entry("stress-10k.osc", stress10k()));
		return List.copyOf(projects);
	}
}
