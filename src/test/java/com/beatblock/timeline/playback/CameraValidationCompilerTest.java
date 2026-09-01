package com.beatblock.timeline.playback;

import com.beatblock.automap.camera.CameraSegmentSemantics;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.camera.CameraTrackFactory;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraValidationCompilerTest {

	@Test
	void strictPolicyBlocksMissingCameraSubject() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		BlockAnimationEngine engine = new BlockAnimationEngine();
		engine.getStageObjectSystem().register(
			StageObjectSystem.fromBlocks("stage", "Stage", List.of(new BlockPos(0, 64, 0))));

		Map<String, Object> semantics = new HashMap<>();
		semantics.put(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_KIND, "STAGE_OBJECT");
		semantics.put(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_REF, "deleted-object");
		CameraTrackFactory.addOrbitSegment(
			timeline, 1.0, 2.0, 0, 64, 0, 8, 3, 0, 90,
			TimelineEventOrigin.MANUAL, semantics);

		TimelineValidationReport report = TimelineValidator.validate(timeline, engine, null);
		assertTrue(report.hasErrors());
		assertTrue(report.problems().stream()
			.anyMatch(d -> TimelineValidator.RULE_MISSING_CAMERA_LOOK_AT.equals(d.ruleId())));

		assertThrows(TimelineCompilationException.class,
			() -> TimelineCompiler.compile(timeline, engine, null, CompilePolicy.STRICT));
	}

	@Test
	void skipPolicyOmitsInvalidCameraSegment() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		BlockAnimationEngine engine = new BlockAnimationEngine();
		engine.getStageObjectSystem().register(
			StageObjectSystem.fromBlocks("stage", "Stage", List.of(new BlockPos(0, 64, 0))));

		Map<String, Object> validSemantics = CameraSegmentSemantics.fromShot(new com.beatblock.automap.camera.CameraShot(
			0.0, 2.0,
			com.beatblock.automap.camera.CameraSubject.stageObject("stage"),
			com.beatblock.automap.camera.CameraShotFraming.MEDIUM,
			com.beatblock.automap.camera.CameraShotMovement.HOLD,
			null,
			com.beatblock.automap.camera.CameraShotTransition.CUT,
			com.beatblock.automap.camera.CameraShotEasing.SMOOTH,
			com.beatblock.automap.camera.CameraCollisionPolicy.AVOID_BLOCKS,
			com.beatblock.automap.camera.CameraShotBeatAlignment.none(),
			0
		));
		CameraTrackFactory.addOrbitSegment(
			timeline, 0.0, 2.0, 0, 64, 0, 8, 3, 0, 90,
			TimelineEventOrigin.MANUAL, validSemantics);

		Map<String, Object> invalidSemantics = new HashMap<>();
		invalidSemantics.put(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_KIND, "STAGE_OBJECT");
		invalidSemantics.put(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_REF, "missing");
		CameraTrackFactory.addOrbitSegment(
			timeline, 4.0, 2.0, 0, 64, 0, 8, 3, 0, 90,
			TimelineEventOrigin.MANUAL, invalidSemantics);

		var invalidEvent = CameraTrackFactory.findSegmentHeadEvent(
			timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().get(1));

		CompileResult result = TimelineCompiler.compile(
			timeline, engine, null, CompilePolicy.SKIP_INVALID_EVENTS);

		assertEquals(List.of(invalidEvent.getId()), result.skippedEventIds());
		assertEquals(1, result.snapshot().cameraTrack().clips().size());
		assertEquals(1, result.snapshot().cameraTrack().clips().getFirst().events().size());
		assertTrue(result.report().hasErrors());
	}
}
