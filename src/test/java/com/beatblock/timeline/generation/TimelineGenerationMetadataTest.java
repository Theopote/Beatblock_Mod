package com.beatblock.timeline.generation;

import com.beatblock.automap.choreography.ChoreographyCompileOptions;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanCompiler;
import com.beatblock.automap.choreography.ChoreographyVfxFactory;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.CameraKeyframe;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineClipOrigin;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.test.WithBeatBlockContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class TimelineGenerationMetadataTest {

	@Test
	void metadataRoundTripsThroughEventParameters() {
		TimelineGenerationMetadata metadata = new TimelineGenerationMetadata(
			TimelineEventOrigin.GENERATED,
			TimelineGeneratorIds.SMART_AUTOMAP,
			"gen-test-1",
			2,
			5,
			"project-abc"
		);
		Map<String, Object> params = TimelineGenerationMetadataSupport.apply(Map.of("kind", "ORBIT"), metadata);

		TimelineGenerationMetadata restored = TimelineGenerationMetadata.fromParameters(params);
		assertEquals(metadata, restored);
	}

	@Test
	void replaceGeneratorPreservesOtherGeneratorContent() {
		Timeline timeline = Timeline.createDefault();

		TimelineGenerationMetadata smartAutomap = new TimelineGenerationMetadata(
			TimelineEventOrigin.GENERATED,
			TimelineGeneratorIds.SMART_AUTOMAP,
			"gen-a",
			0,
			-1,
			""
		);
		TimelineGenerationMetadata aiDirector = new TimelineGenerationMetadata(
			TimelineEventOrigin.GENERATED,
			TimelineGeneratorIds.AI_DIRECTOR,
			"gen-b",
			1,
			-1,
			""
		);

		CameraTrackFactory.addOrbitSegment(
			timeline, 1.0, 2.0,
			0, 64, 0,
			8, 4, 0, 90,
			smartAutomap,
			null
		);
		CameraTrackFactory.addOrbitSegment(
			timeline, 4.0, 2.0,
			0, 64, 0,
			8, 4, 0, 90,
			aiDirector,
			null
		);
		assertEquals(2, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());

		timeline.applyContentReplacePolicy(
			Timeline.TRACK_ID_CAMERA,
			ContentReplacePolicy.replaceGenerator(TimelineGeneratorIds.SMART_AUTOMAP, false)
		);

		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
		var remaining = TimelineClipOrigin.metadataFromClip(
			timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst(),
			Timeline.TRACK_ID_CAMERA
		);
		assertEquals(TimelineGeneratorIds.AI_DIRECTOR, remaining.generatorId());
	}

	@Test
	void smartAutoMapCompileStampsGeneratorAndGenerationBatch() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("projectId", "golden-test");

		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.INTRO, "intro")),
			List.of(),
			List.of(new ChoreographyPlan.MotionPhrase(
				1.0, "kick", "low", 0.6f, "bounce", 0.5, true, 4f, 0.0, 0
			)),
			List.of(new ChoreographyPlan.CameraPhrase(4.0, "PAN", 0)),
			List.of(ChoreographyVfxFactory.fromLegacyVfxKind(2.0, "particle_spark", 0)),
			DensityCurve.uniform(1.0)
		);

		ChoreographyPlanCompiler.compileAll(timeline, plan, ChoreographyCompileOptions.smartAutoMap());

		assertFalse(timeline.getAutoAnimationEvents().isEmpty());
		Map<String, Object> animationParams = timeline.getAutoAnimationEvents().getFirst().getParameters();
		assertEquals(TimelineGeneratorIds.SMART_AUTOMAP, animationParams.get(TimelineGenerationMetadataSupport.PARAM_GENERATOR_ID));
		assertNotEquals("", animationParams.get(TimelineGenerationMetadataSupport.PARAM_GENERATION_ID));
		assertEquals(0, ((Number) animationParams.get(TimelineGenerationMetadataSupport.PARAM_SECTION_INDEX)).intValue());

		var cameraClip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		var cameraMetadata = TimelineClipOrigin.metadataFromClip(cameraClip, Timeline.TRACK_ID_CAMERA);
		assertEquals(TimelineGeneratorIds.SMART_AUTOMAP, cameraMetadata.generatorId());
		assertEquals("golden-test", cameraMetadata.sourcePlanId());

		var globalClip = timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().getFirst();
		var globalMetadata = TimelineClipOrigin.metadataFromClip(globalClip, Timeline.TRACK_ID_GLOBAL);
		assertEquals(TimelineGeneratorIds.SMART_AUTOMAP, globalMetadata.generatorId());
		assertTrue(globalMetadata.generationId().startsWith("gen-"));
	}

	@Test
	void withMetadataTagsAnimationEventParameters() {
		var source = new TimelineAnimationEvent(
			"ev1", 1.0, 0.5, "bounce", "stage", 0.8f, Map.of("energy", 0.8f));
		var metadata = new TimelineGenerationMetadata(
			TimelineEventOrigin.GENERATED,
			TimelineGeneratorIds.SMART_AUTOMAP,
			"gen-xyz",
			3,
			7,
			"plan-1"
		);

		var tagged = TimelineDraftWriter.withMetadata(source, metadata);

		assertEquals(TimelineGeneratorIds.SMART_AUTOMAP,
			tagged.getParameters().get(TimelineGenerationMetadataSupport.PARAM_GENERATOR_ID));
		assertEquals("gen-xyz", tagged.getParameters().get(TimelineGenerationMetadataSupport.PARAM_GENERATION_ID));
		assertEquals(3, ((Number) tagged.getParameters().get(TimelineGenerationMetadataSupport.PARAM_SECTION_INDEX)).intValue());
		assertEquals(7, ((Number) tagged.getParameters().get(TimelineGenerationMetadataSupport.PARAM_PHRASE_INDEX)).intValue());
	}

	@Test
	void recompileUsesSameReplaceGeneratorPolicyAndPreservesManualCamera() {
		Timeline timeline = Timeline.createDefault();
		timeline.addCameraKeyframe(new CameraKeyframe(1.0));

		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.INTRO, "intro")),
			List.of(),
			List.of(),
			List.of(new ChoreographyPlan.CameraPhrase(4.0, "PAN", 0)),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		var options = ChoreographyCompileOptions.smartAutoMap();

		ChoreographyPlanCompiler.compileAll(timeline, plan, options);
		ChoreographyPlanCompiler.compileAll(timeline, plan, options);

		assertEquals(2, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
		long autoClips = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().stream()
			.filter(clip -> TimelineClipOrigin.isAutoGenerated(clip, Timeline.TRACK_ID_CAMERA))
			.count();
		assertEquals(1, autoClips);
	}

	@Test
	void replaceGeneratorDoesNotRemoveImportedContent() {
		Timeline timeline = Timeline.createDefault();
		TimelineGenerationMetadata imported = new TimelineGenerationMetadata(
			TimelineEventOrigin.IMPORTED,
			"osc-import",
			"gen-import-1",
			0,
			-1,
			""
		);
		CameraTrackFactory.addOrbitSegment(
			timeline, 1.0, 2.0,
			0, 64, 0,
			8, 4, 0, 90,
			imported,
			null
		);
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());

		timeline.applyContentReplacePolicy(
			Timeline.TRACK_ID_CAMERA,
			ContentReplacePolicy.replaceGenerator(TimelineGeneratorIds.SMART_AUTOMAP, false)
		);

		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
	}
}
