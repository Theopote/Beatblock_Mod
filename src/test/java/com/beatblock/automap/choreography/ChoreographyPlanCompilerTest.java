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
		int count = ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, false);

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
		ChoreographyPlanCompiler.compileAnimationEvents(timelineA, plan, false);
		ChoreographyPlanCompiler.compileAnimationEvents(timelineB, plan, false);

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

		int count = ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, false);

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
	}
}
