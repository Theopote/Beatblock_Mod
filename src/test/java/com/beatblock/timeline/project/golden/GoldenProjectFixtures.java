package com.beatblock.timeline.project.golden;

import com.beatblock.BeatBlock;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.automap.camera.CameraSegmentSemantics;
import com.beatblock.automap.choreography.ChoreographyCompileOptions;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanCompiler;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.ChoreographyVfx;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.MotifAxis;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.choreography.grammar.TargetSet;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.FeatureEvent;
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

	/**
	 * Creator Alpha Showcase：80s / 8 towers / 全段落，含 Accent·Phrase·Hero、
	 * ≥4 spatial patterns、camera continuity 候选、VFX/particle、MANUAL 痕迹与真实 beat grid。
	 */
	public static GoldenProjectContext creatorAlphaShowcase() {
		final double duration = 80.0;
		final double bpm = 128.0;
		final double beat = 60.0 / bpm;
		Timeline timeline = baseTimeline("Creator Alpha Showcase", "golden-creator-alpha-showcase", duration, bpm);
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());

		String[] towerIds = new String[8];
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI * 2.0 / 8.0;
			int x = (int) Math.round(Math.cos(angle) * 8.0);
			int z = (int) Math.round(Math.sin(angle) * 8.0);
			String id = "Tower_" + (char) ('A' + i);
			towerIds[i] = id;
			RuntimeStageObject tower = StageObjectSystem.fromBlocks(
				id, id, List.of(new BlockPos(x, 64, z), new BlockPos(x, 65, z)));
			layers.registerRestored(new BuildLayer(
				"layer-" + id.toLowerCase(), id, tower, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));
		}

		List<ChoreographyPlan.SectionPlan> sections = List.of(
			new ChoreographyPlan.SectionPlan(0, 8, SectionType.INTRO, "Intro"),
			new ChoreographyPlan.SectionPlan(8, 20, SectionType.VERSE, "Verse"),
			new ChoreographyPlan.SectionPlan(20, 28, SectionType.PRE_CHORUS, "Pre-Chorus"),
			new ChoreographyPlan.SectionPlan(28, 40, SectionType.CHORUS, "Chorus"),
			new ChoreographyPlan.SectionPlan(40, 48, SectionType.BUILD, "Build"),
			new ChoreographyPlan.SectionPlan(48, 58, SectionType.DROP, "Drop"),
			new ChoreographyPlan.SectionPlan(58, 66, SectionType.BREAK, "Break"),
			new ChoreographyPlan.SectionPlan(66, 80, SectionType.DROP, "Final")
		);
		for (ChoreographyPlan.SectionPlan section : sections) {
			MarkerType markerType = section.sectionType() == SectionType.DROP ? MarkerType.DROP : MarkerType.SECTION;
			timeline.addMarker(new TimelineMarker(
				"sec-" + section.label().toLowerCase().replace(' ', '-'),
				section.startSeconds(),
				section.label(),
				markerType));
		}

		List<Double> beatTimes = new ArrayList<>();
		for (double t = 0.0; t <= duration + 1e-6; t += beat) {
			beatTimes.add(Math.round(t * 1000.0) / 1000.0);
		}
		for (double t = 0.0; t < duration; t += beat) {
			float energy = t >= 48.0 ? 0.95f : (t >= 28.0 ? 0.85f : 0.65f);
			timeline.addFeatureEvent("kick", new FeatureEvent(t, energy));
		}

		TargetSet allTowers = TargetSet.of(towerIds);
		TargetSet front = TargetSet.of(towerIds[0], towerIds[1], towerIds[2], towerIds[3]);
		TargetSet back = TargetSet.of(towerIds[4], towerIds[5], towerIds[6], towerIds[7]);

		List<ChoreographyPlan.StageRoleAssignment> roles = List.of(
			new ChoreographyPlan.StageRoleAssignment("low", towerIds[0]),
			new ChoreographyPlan.StageRoleAssignment("mid", towerIds[1]),
			new ChoreographyPlan.StageRoleAssignment("high", towerIds[2])
		);

		List<ChoreographyPlan.MotionPhrase> accents = List.of(
			new ChoreographyPlan.MotionPhrase(2.0, "kick", "low", 0.35f, "pulse", 0.35, true, 2f, 0.25, 0),
			new ChoreographyPlan.MotionPhrase(10.0, "kick", "mid", 0.4f, "pulse", 0.35, true, 2f, 0.25, 1),
			new ChoreographyPlan.MotionPhrase(22.0, "kick", "high", 0.45f, "pulse", 0.4, true, 2f, 0.2, 2),
			new ChoreographyPlan.MotionPhrase(32.0, "kick", "low", 0.4f, "pulse", 0.35, true, 2f, 0.2, 3),
			new ChoreographyPlan.MotionPhrase(44.0, "kick", "mid", 0.5f, "pulse", 0.4, true, 2f, 0.15, 4),
			new ChoreographyPlan.MotionPhrase(60.0, "kick", "low", 0.35f, "pulse", 0.35, true, 2f, 0.3, 6)
		);

		List<com.beatblock.automap.choreography.grammar.ChoreographyPhrase> grammar = new ArrayList<>();
		grammar.add(new com.beatblock.automap.choreography.grammar.ChoreographyPhrase(
			new com.beatblock.automap.choreography.grammar.TriggerSpec.EveryNBeats(4),
			front,
			com.beatblock.automap.choreography.grammar.SpatialPatternSpec.of(
				SpatialMotifId.CASCADE, MotifAxis.X),
			com.beatblock.automap.choreography.grammar.MotionPresetSpec.bounce(),
			com.beatblock.automap.choreography.grammar.TimingPatternSpec.stagger(0.08),
			com.beatblock.automap.choreography.grammar.IntensityEnvelope.flat(0.75f),
			com.beatblock.automap.choreography.grammar.VariationSpec.none(),
			1
		));
		grammar.add(new com.beatblock.automap.choreography.grammar.ChoreographyPhrase(
			new com.beatblock.automap.choreography.grammar.TriggerSpec.EveryNFeatureHits("kick", 4),
			allTowers,
			com.beatblock.automap.choreography.grammar.SpatialPatternSpec.of(
				SpatialMotifId.WAVE, MotifAxis.Z),
			com.beatblock.automap.choreography.grammar.MotionPresetSpec.bounce(),
			com.beatblock.automap.choreography.grammar.TimingPatternSpec.stagger(0.06),
			com.beatblock.automap.choreography.grammar.IntensityEnvelope.crescendo(0.6f, 0.95f),
			com.beatblock.automap.choreography.grammar.VariationSpec.none(),
			3
		));
		grammar.add(new com.beatblock.automap.choreography.grammar.ChoreographyPhrase(
			new com.beatblock.automap.choreography.grammar.TriggerSpec.EveryNBeats(2),
			back,
			com.beatblock.automap.choreography.grammar.SpatialPatternSpec.of(
				SpatialMotifId.ALTERNATE, MotifAxis.X),
			new com.beatblock.automap.choreography.grammar.MotionPresetSpec("pulse", 0.45, true, 3f),
			com.beatblock.automap.choreography.grammar.TimingPatternSpec.stagger(0.05),
			com.beatblock.automap.choreography.grammar.IntensityEnvelope.flat(0.8f),
			com.beatblock.automap.choreography.grammar.VariationSpec.none(),
			4
		));
		grammar.add(new com.beatblock.automap.choreography.grammar.ChoreographyPhrase(
			new com.beatblock.automap.choreography.grammar.TriggerSpec.EveryNBeats(4),
			front,
			com.beatblock.automap.choreography.grammar.SpatialPatternSpec.of(
				SpatialMotifId.EXPLODE, MotifAxis.RADIAL),
			new com.beatblock.automap.choreography.grammar.MotionPresetSpec("bounce", 0.5, true, 4f),
			new com.beatblock.automap.choreography.grammar.TimingPatternSpec.Simultaneous(),
			com.beatblock.automap.choreography.grammar.IntensityEnvelope.flat(0.7f),
			com.beatblock.automap.choreography.grammar.VariationSpec.none(),
			6
		));

		var dropHero = com.beatblock.automap.choreography.grammar.ChoreographyHeroSelection.phraseForSection(
			5, SectionType.DROP, allTowers);
		var finalHero = com.beatblock.automap.choreography.grammar.ChoreographyHeroSelection.phraseForSection(
			7, SectionType.DROP, allTowers);
		if (dropHero != null) grammar.add(dropHero);
		if (finalHero != null) grammar.add(finalHero);

		List<ChoreographyPlan.CameraPhrase> cameras = List.of(
			new ChoreographyPlan.CameraPhrase(
				0.0, "WIDE", 0, "STAGE_OBJECT", towerIds[0], 7.0,
				"WIDE", "STATIC", "SMOOTH", true, ChoreographyTimingSnap.BAR, "CUT"),
			new ChoreographyPlan.CameraPhrase(
				8.0, "ORBIT", 1, "STAGE_OBJECT", towerIds[2], 6.0,
				"MEDIUM", "ORBIT", "SMOOTH", true, ChoreographyTimingSnap.BAR, "SMOOTH_MOVE"),
			new ChoreographyPlan.CameraPhrase(
				28.0, "PUSH", 3, "STAGE_OBJECT", towerIds[4], 4.0,
				"CLOSE", "DOLLY_IN", "EASE_OUT", true, ChoreographyTimingSnap.BEAT, "CUT"),
			new ChoreographyPlan.CameraPhrase(
				48.0, "WHIP_IN", 5, "STAGE_OBJECT", towerIds[0], 2.0,
				"WIDE", "WHIP", "LINEAR", false, ChoreographyTimingSnap.BEAT, "WHIP"),
			new ChoreographyPlan.CameraPhrase(
				66.0, "HERO", 7, "STAGE_OBJECT", towerIds[3], 5.0,
				"WIDE", "ORBIT", "SMOOTH", true, ChoreographyTimingSnap.BAR, "DISSOLVE")
		);

		List<ChoreographyVfx> vfx = List.of(
			new ChoreographyVfx.ParticleBurst(
				29.0, "Chorus Spark", "minecraft:crit",
				com.beatblock.automap.camera.CameraSubject.stageObject(towerIds[0]),
				16, 0.6, 0.05, 3),
			new ChoreographyVfx.ScreenFlash(48.5, "Drop Flash", 1f, 1f, 1f, 0.2, 5),
			new ChoreographyVfx.ParticleBurst(
				49.0, "Drop Burst", "minecraft:firework",
				com.beatblock.automap.camera.CameraSubject.allStageObjects(),
				24, 0.8, 0.06, 5),
			new ChoreographyVfx.ScreenTint(66.5, "Final Tint", 0.35, 0.2f, 0.45f, 1f, 4.0, 7)
		);

		DensityCurve density = DensityCurve.ofPoints(List.of(
			new DensityCurve.Point(0, 0.20),
			new DensityCurve.Point(8, 0.40),
			new DensityCurve.Point(20, 0.55),
			new DensityCurve.Point(28, 0.75),
			new DensityCurve.Point(40, 0.65),
			new DensityCurve.Point(48, 1.00),
			new DensityCurve.Point(58, 0.35),
			new DensityCurve.Point(66, 0.95),
			new DensityCurve.Point(80, 0.50)
		));

		ChoreographyPlan.MusicalStructure musical = new ChoreographyPlan.MusicalStructure(
			List.of(), List.of(), List.of(), beatTimes);

		ChoreographyPlan plan = new ChoreographyPlan(
			sections,
			roles,
			accents,
			cameras,
			vfx,
			density,
			List.of(),
			musical,
			List.of(),
			List.copyOf(grammar)
		);
		ChoreographyPlanStore.save(timeline, plan, null);
		registerShowcaseStagesForCompile(layers);
		ChoreographyPlanCompiler.compileAll(timeline, plan, ChoreographyCompileOptions.smartAutoMap());

		timeline.addAutoAnimationEvent(
			new TimelineAnimationEvent(
				"manual-showcase-accent",
				15.5,
				0.5,
				"Pulse",
				towerIds[0],
				0.55f,
				Map.of(
					"animationType", "Pulse",
					"targetObject", towerIds[0],
					"durationSeconds", 0.5,
					"actionMode", "ANIMATE",
					"playbackSemantics", "TRANSIENT",
					"eventOrigin", TimelineEventOrigin.MANUAL.name(),
					"energy", 0.55f
				)
			)
		);

		return new GoldenProjectContext(
			timeline,
			layers,
			new double[] {0.0, 10.0, 30.0, 50.0, 70.0, 80.0}
		);
	}

	/** 将 Showcase 舞台对象注入 BeatBlock 引擎，供 {@code compileAll} 解析目标与空间排序。 */
	private static void registerShowcaseStagesForCompile(BuildLayerManager layers) {
		if (layers == null || BeatBlock.getContext() == null
			|| BeatBlock.getContext().blockAnimationEngine() == null) {
			return;
		}
		StageObjectSystem stages = BeatBlock.getContext().blockAnimationEngine().getStageObjectSystem();
		stages.clear();
		for (BuildLayer layer : layers.getAll()) {
			if (layer != null && layer.getStageObject() != null) {
				stages.register(layer.getStageObject());
			}
		}
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
		projects.add(Map.entry("creator-alpha-showcase.osc", creatorAlphaShowcase()));
		projects.add(Map.entry("stress-10k.osc", stress10k()));
		return List.copyOf(projects);
	}
}
