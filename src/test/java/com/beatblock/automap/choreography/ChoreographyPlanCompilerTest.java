package com.beatblock.automap.choreography;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapRule;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.CameraKeyframe;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.GlobalEvent;
import com.beatblock.timeline.GlobalEventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.playback.GlobalEventPayloadCodec;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.project.golden.GoldenProjectEventCounts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@WithBeatBlockContext
class ChoreographyPlanCompilerTest {

	@Test
	void compilesPerFeatureTargetsFromPlanStageRoles() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.6f));
		timeline.addFeatureEvent("snare", new FeatureEvent(1.1, 0.5f));

		AutoMapConfig config = AutoMapConfig.builder()
			.minGapSeconds(0.08)
			.rule(new AutoMapRule("low", 0.15f, "bounce", 0.5, true, 4f, 0.12, null))
			.rule(new AutoMapRule("mid", 0.2f, "slide", 0.4, true, 3f, 0.08, null))
			.rule(new AutoMapRule("high", 0.15f, "pulse", 0.3, false, 1f, 0.04, null))
			.targetForFeature("low", "stage-kick")
			.targetForFeature("mid", "stage-snare")
			.build();

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromTimeline(timeline, config);
		int count = ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, ReplaceMode.APPEND);

		assertEquals(2, count);
		assertEquals("stage-kick", timeline.getAutoAnimationEvents().get(0).getTargetObjectId());
		assertEquals("stage-snare", timeline.getAutoAnimationEvents().get(1).getTargetObjectId());
	}

	@Test
	void samePlanCompilesSameTargetsWithoutAutoMapConfig() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.INTRO, "intro")),
			List.of(
				new ChoreographyPlan.StageRoleAssignment("low", "stage-kick"),
				new ChoreographyPlan.StageRoleAssignment("mid", "stage-snare")
			),
			List.of(
				new ChoreographyPlan.MotionPhrase(1.0, "kick", "low", 0.6f, "bounce", 0.5, true, 4f, 0),
				new ChoreographyPlan.MotionPhrase(1.2, "snare", "mid", 0.5f, "slide", 0.4, true, 3f, 0)
			),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);

		Timeline timelineA = Timeline.createDefault();
		Timeline timelineB = Timeline.createDefault();
		ChoreographyPlanCompiler.compileAnimationEvents(timelineA, plan, ReplaceMode.APPEND);
		ChoreographyPlanCompiler.compileAnimationEvents(timelineB, plan, ReplaceMode.APPEND);

		assertEquals(
			timelineA.getAutoAnimationEvents().get(0).getTargetObjectId(),
			timelineB.getAutoAnimationEvents().get(0).getTargetObjectId()
		);
		assertEquals("stage-kick", timelineA.getAutoAnimationEvents().get(0).getTargetObjectId());
		assertEquals("stage-snare", timelineA.getAutoAnimationEvents().get(1).getTargetObjectId());
	}

	@Test
	void skipsMotionPhrasesInVeryLowDensitySections() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("low", new FeatureEvent(2.0, 0.9f));

		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 10, com.beatblock.automap.engine.SectionType.INTRO, "intro")),
			List.of(),
			List.of(new ChoreographyPlan.MotionPhrase(
				2.0, "low", "low", 0.9f, "bounce", 0.5, true, 4f)),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.1)
		);

		int count = ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, ReplaceMode.APPEND);

		assertEquals(0, count);
	}

	@Test
	void compileAllReplaceGeneratedIsIdempotentForCameraAndVfx() {
		Timeline timeline = Timeline.createDefault();
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.INTRO, "intro")),
			List.of(),
			List.of(),
			List.of(new ChoreographyPlan.CameraPhrase(4.0, "PAN", 0)),
			List.of(
				ChoreographyVfxFactory.fromLegacyVfxKind(2.0, "particle_spark", 0),
				ChoreographyVfxFactory.fromLegacyVfxKind(8.0, "particle_burst", 0)
			),
			DensityCurve.uniform(1.0)
		);
		var options = ChoreographyCompileOptions.smartAutoMap();

		var first = ChoreographyPlanCompiler.compileAll(timeline, plan, options);
		var second = ChoreographyPlanCompiler.compileAll(timeline, plan, options);

		assertEquals(1, first.cameraEvents());
		assertEquals(2, first.vfxEvents());
		assertEquals(first.cameraEvents(), second.cameraEvents());
		assertEquals(first.vfxEvents(), second.vfxEvents());
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
		assertEquals(2, timeline.getGlobalEvents().size());
	}

	@Test
	void compileAllReplaceGeneratedPreservesManualCameraAndVfx() {
		Timeline timeline = Timeline.createDefault();
		timeline.addCameraKeyframe(new CameraKeyframe(1.0));
		timeline.addGlobalEvent(new GlobalEvent(1.5, GlobalEventType.SCREEN_TINT, "Manual"));

		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.INTRO, "intro")),
			List.of(),
			List.of(),
			List.of(new ChoreographyPlan.CameraPhrase(4.0, "PAN", 0)),
			List.of(ChoreographyVfxFactory.fromLegacyVfxKind(6.0, "particle_spark", 0)),
			DensityCurve.uniform(1.0)
		);

		ChoreographyPlanCompiler.compileAll(
			timeline, plan, ChoreographyCompileOptions.smartAutoMap());
		assertEquals(2, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
		assertEquals(2, timeline.getGlobalEvents().size());

		ChoreographyPlanCompiler.compileAll(
			timeline, plan, ChoreographyCompileOptions.smartAutoMap());
		assertEquals(2, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
		assertEquals(2, timeline.getGlobalEvents().size());
		assertEquals("Manual", timeline.getGlobalEvents().stream()
			.filter(event -> "Manual".equals(event.getName()))
			.findFirst()
			.orElseThrow()
			.getName());
	}

	@Test
	void preservesOffBeatMotionTimesWhenTimingSnapIsNone() {
		Timeline timeline = Timeline.createDefault();
		var musical = new ChoreographyPlan.MusicalStructure(
			List.of(new ChoreographyPlan.BarPlan(0.0, 2.0, 0, 0)),
			List.of(),
			List.of()
		);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.INTRO, "intro")),
			List.of(),
			List.of(new ChoreographyPlan.MotionPhrase(
				1.5, "hihat", "high", 0.6f, "pulse", 0.3, false, 1f, 0.0, 0,
				ChoreographyTimingSnap.NONE
			)),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			musical
		);

		int count = ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, ReplaceMode.APPEND);

		assertEquals(1, count);
		assertEquals(1.5, timeline.getAutoAnimationEvents().get(0).getTimeSeconds(), 1e-6);
	}

	@Test
	void snapsMotionToBarStartOnlyWhenTimingSnapIsBar() {
		Timeline timeline = Timeline.createDefault();
		var musical = new ChoreographyPlan.MusicalStructure(
			List.of(
				new ChoreographyPlan.BarPlan(0.0, 2.0, 0, 0),
				new ChoreographyPlan.BarPlan(2.0, 4.0, 1, 0)
			),
			List.of(),
			List.of()
		);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.BUILD, "build")),
			List.of(),
			List.of(new ChoreographyPlan.MotionPhrase(
				2.05, "build", "low", 0.8f, "bounce", 0.5, true, 4f, 0.0, 0,
				ChoreographyTimingSnap.BAR
			)),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			musical
		);

		ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, ReplaceMode.APPEND);

		assertEquals(2.0, timeline.getAutoAnimationEvents().get(0).getTimeSeconds(), 1e-6);
	}

	@Test
	void compilesVfxAsTypedParticleBurstPayload() {
		Timeline timeline = Timeline.createDefault();
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.INTRO, "intro")),
			List.of(),
			List.of(),
			List.of(),
			List.of(ChoreographyVfxFactory.fromLegacyVfxKind(2.0, "particle_spark", 0)),
			DensityCurve.uniform(1.0)
		);

		ChoreographyPlanCompiler.compileAll(
			timeline, plan, ChoreographyCompileOptions.smartAutoMap());

		var event = timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().getFirst().getEvents().getFirst();
		assertEquals("PARTICLE_BURST", event.getParameters().get("type"));
		GlobalEventPayload.ParticleBurst payload = assertInstanceOf(
			GlobalEventPayload.ParticleBurst.class,
			GlobalEventPayloadCodec.decode(event.getParameters())
		);
		assertEquals("spark", payload.name());
		assertEquals("minecraft:crit", payload.particleType());
		assertEquals(12, payload.count());
		assertEquals(0.5, payload.spread(), 1e-9);
		assertEquals(0.04, payload.speed(), 1e-9);
	}

	@Test
	void appendAnimationsReplaceCameraAndVfxPreservesExistingAnimations() {
		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new com.beatblock.timeline.TimelineAnimationEvent(
			"manual-auto", 0.5, 1.0, "bounce", "stage", 1f, java.util.Map.of()));

		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.INTRO, "intro")),
			List.of(),
			List.of(new ChoreographyPlan.MotionPhrase(1.0, "kick", "low", 0.6f, "bounce", 0.5, true, 4f, 0.0, 0)),
			List.of(new ChoreographyPlan.CameraPhrase(4.0, "PAN", 0)),
			List.of(ChoreographyVfxFactory.fromLegacyVfxKind(2.0, "particle_spark", 0)),
			DensityCurve.uniform(1.0)
		);

		ChoreographyPlanCompiler.compileAll(timeline, plan, ChoreographyCompileOptions.appendAnimationsReplaceCameraAndVfx());

		assertEquals(2, timeline.getAutoAnimationEvents().size());
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
		assertEquals(1, timeline.getGlobalEvents().size());
	}

	@Test
	void compileSectionOnlyReplacesTargetSectionTimelineContent() {
		Timeline timeline = Timeline.createDefault();
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 8, SectionType.INTRO, "intro"),
				new ChoreographyPlan.SectionPlan(8, 16, SectionType.DROP, "drop")
			),
			List.of(),
			List.of(
				new ChoreographyPlan.MotionPhrase(1.0, "intro", "low", 0.6f, "bounce", 0.5, true, 4f, 0.0, 0),
				new ChoreographyPlan.MotionPhrase(9.0, "drop", "low", 0.8f, "pulse", 0.5, true, 4f, 0.0, 1)
			),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		ChoreographyPlanCompiler.compileAll(timeline, plan, ChoreographyCompileOptions.smartAutoMap());
		assertEquals(2, timeline.getAutoAnimationEvents().size());
		assertEquals("bounce", timeline.getAutoAnimationEvents().get(0).getAnimationTypeId());
		assertEquals("pulse", timeline.getAutoAnimationEvents().get(1).getAnimationTypeId());

		ChoreographyPlan updated = new ChoreographyPlan(
			plan.sections(),
			plan.stageRoles(),
			List.of(
				new ChoreographyPlan.MotionPhrase(1.0, "intro", "low", 0.6f, "spin", 0.5, true, 4f, 0.0, 0),
				new ChoreographyPlan.MotionPhrase(9.0, "drop", "low", 0.8f, "pulse", 0.5, true, 4f, 0.0, 1)
			),
			plan.cameraPhrases(),
			plan.vfxPhrases(),
			plan.densityCurve(),
			plan.sectionEdits(),
			plan.musicalStructure()
		);
		ChoreographyPlanCompiler.compileSection(timeline, updated, 0);

		assertEquals(2, timeline.getAutoAnimationEvents().size());
		assertEquals("spin", timeline.getAutoAnimationEvents().get(0).getAnimationTypeId());
		assertEquals("pulse", timeline.getAutoAnimationEvents().get(1).getAnimationTypeId());
		assertEquals(9.0, timeline.getAutoAnimationEvents().get(1).getTimeSeconds(), 1e-6);
	}

	@Test
	void replaceGeneratedClearsAutoCameraAndVfxWhenPlanBecomesEmpty() {
		Timeline timeline = Timeline.createDefault();
		timeline.addCameraKeyframe(new CameraKeyframe(0.5));
		timeline.addGlobalEvent(new GlobalEvent(0.75, GlobalEventType.SCREEN_TINT, "Manual Vfx"));

		List<ChoreographyPlan.CameraPhrase> cameras = new java.util.ArrayList<>();
		for (int i = 0; i < 10; i++) {
			cameras.add(new ChoreographyPlan.CameraPhrase(1.0 + i, "PAN", 0));
		}
		List<ChoreographyVfx> vfx = new java.util.ArrayList<>();
		for (int i = 0; i < 20; i++) {
			vfx.add(ChoreographyVfxFactory.fromLegacyVfxKind(0.5 + i * 0.25, "particle_spark", 0));
		}
		ChoreographyPlan populated = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 32, SectionType.INTRO, "intro")),
			List.of(),
			List.of(),
			cameras,
			vfx,
			DensityCurve.uniform(1.0)
		);
		var options = ChoreographyCompileOptions.smartAutoMap();

		ChoreographyPlanCompiler.compileAll(timeline, populated, options);
		assertEquals(new GoldenProjectEventCounts.OriginCounts(1, 10),
			GoldenProjectEventCounts.camera(timeline));
		assertEquals(new GoldenProjectEventCounts.OriginCounts(1, 20),
			GoldenProjectEventCounts.global(timeline));

		ChoreographyPlan empty = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 32, SectionType.INTRO, "intro")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		ChoreographyPlanCompiler.compileAll(timeline, empty, options);

		assertEquals(new GoldenProjectEventCounts.OriginCounts(1, 0),
			GoldenProjectEventCounts.camera(timeline));
		assertEquals(new GoldenProjectEventCounts.OriginCounts(1, 0),
			GoldenProjectEventCounts.global(timeline));
		assertEquals("Manual Vfx", timeline.getGlobalEvents().getFirst().getName());
	}
}
