package com.beatblock.automap.camera;

import com.beatblock.BeatBlock;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.camera.CameraTrackFactory;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithBeatBlockContext
class CameraShotTimelineWriterTest {

	@Test
	void writesShotSemanticsOntoSegment() {
		BeatBlock.getContext().blockAnimationEngine().getStageObjectSystem().register(
			StageObjectSystem.fromBlocks("stage-a", "Stage A", List.of(new BlockPos(1, 64, 2))));

		Timeline timeline = Timeline.createDefault();
		CameraShot shot = new CameraShot(
			2.0,
			3.0,
			CameraSubject.animatedTarget("stage-a"),
			CameraShotFraming.MEDIUM,
			CameraShotMovement.ORBIT,
			null,
			CameraShotTransition.SMOOTH_MOVE,
			CameraShotEasing.EASE_OUT,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			0
		);

		int count = CameraShotTimelineWriter.write(timeline, List.of(shot));

		assertEquals(1, count);
		var segment = CameraTrackFactory.findSegmentHeadEvent(
			timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst());
		assertNotNull(segment);
		assertEquals("EASE_OUT", segment.getParameter(CameraSegmentSemantics.KEY_EASE));
		assertEquals("SMOOTH_MOVE", segment.getParameter(CameraSegmentSemantics.KEY_TRANSITION));
		assertEquals("AVOID_BLOCKS", segment.getParameter(CameraSegmentSemantics.KEY_COLLISION_POLICY));
		assertEquals("ANIMATED_TARGET", segment.getParameter(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_KIND));
		assertEquals("stage-a", segment.getParameter(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_REF));
	}

	@Test
	void framingDistanceScalesWithStageObjectSize() {
		BeatBlock.getContext().blockAnimationEngine().getStageObjectSystem().register(
			StageObjectSystem.fromBlocks("stage-small", "Small", List.of(new BlockPos(0, 64, 0))));
		BeatBlock.getContext().blockAnimationEngine().getStageObjectSystem().register(
			StageObjectSystem.fromBlocks("stage-large", "Large", List.of(
				new BlockPos(0, 64, 0),
				new BlockPos(29, 81, 24)
			)));

		Timeline smallTimeline = Timeline.createDefault();
		Timeline largeTimeline = Timeline.createDefault();
		CameraShot smallShot = orbitShot("stage-small");
		CameraShot largeShot = orbitShot("stage-large");

		CameraShotTimelineWriter.write(smallTimeline, List.of(smallShot));
		CameraShotTimelineWriter.write(largeTimeline, List.of(largeShot));

		double smallRadius = orbitRadius(smallTimeline);
		double largeRadius = orbitRadius(largeTimeline);
		org.junit.jupiter.api.Assertions.assertTrue(largeRadius > smallRadius);
	}

	private static CameraShot orbitShot(String stageId) {
		return new CameraShot(
			2.0,
			3.0,
			CameraSubject.animatedTarget(stageId),
			CameraShotFraming.MEDIUM,
			CameraShotMovement.ORBIT,
			null,
			CameraShotTransition.SMOOTH_MOVE,
			CameraShotEasing.EASE_OUT,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			0
		);
	}

	private static double orbitRadius(Timeline timeline) {
		var segment = CameraTrackFactory.findSegmentHeadEvent(
			timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst());
		Object radius = segment.getParameters().get("radius");
		return radius instanceof Number number ? number.doubleValue() : 0.0;
	}

	@Test
	void skipsShotWithMissingSubject() {
		Timeline timeline = Timeline.createDefault();
		CameraShot shot = new CameraShot(
			2.0,
			3.0,
			CameraSubject.stageObject("missing"),
			CameraShotFraming.MEDIUM,
			CameraShotMovement.ORBIT,
			null,
			CameraShotTransition.SMOOTH_MOVE,
			CameraShotEasing.EASE_OUT,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			0
		);

		assertEquals(0, CameraShotTimelineWriter.write(timeline, List.of(shot)));
	}

	@Test
	void poseAnchoredHoldUsesCapturedEyeAndYaw() {
		Timeline timeline = Timeline.createDefault();
		CameraShotDraft draft = CameraShotDraft.fromLivePose(
			1.0,
			4.0,
			CameraShotMovement.HOLD,
			new CapturedCameraPose(11.5, 72.0, -3.25, 45.0, -12.0)
		);

		assertEquals(1, CameraShotTimelineWriter.writeDrafts(timeline, List.of(draft)));

		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		var keyframe = clip.getEvents().stream()
			.filter(e -> e.getType() == com.beatblock.timeline.EventType.CAMERA_KEYFRAME)
			.findFirst()
			.orElseThrow();
		assertEquals(11.5, ((Number) keyframe.getParameter("x")).doubleValue(), 1e-6);
		assertEquals(72.0, ((Number) keyframe.getParameter("y")).doubleValue(), 1e-6);
		assertEquals(-3.25, ((Number) keyframe.getParameter("z")).doubleValue(), 1e-6);
		assertEquals(45.0, ((Number) keyframe.getParameter("yawDeg")).doubleValue(), 1e-6);
		assertEquals(-12.0, ((Number) keyframe.getParameter("pitchDeg")).doubleValue(), 1e-6);
	}

	@Test
	void poseAnchoredDollyUsesCapturedEyeAndYaw() {
		Timeline timeline = Timeline.createDefault();
		CameraShotDraft draft = CameraShotDraft.fromLivePose(
			0.5,
			3.0,
			CameraShotMovement.PUSH_IN,
			new CapturedCameraPose(5.0, 66.0, 8.0, 90.0, 0.0)
		);

		assertEquals(1, CameraShotTimelineWriter.writeDrafts(timeline, List.of(draft)));

		var segment = CameraTrackFactory.findSegmentHeadEvent(
			timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst());
		assertNotNull(segment);
		assertEquals(5.0, ((Number) segment.getParameter("startX")).doubleValue(), 1e-6);
		assertEquals(66.0, ((Number) segment.getParameter("startY")).doubleValue(), 1e-6);
		assertEquals(8.0, ((Number) segment.getParameter("startZ")).doubleValue(), 1e-6);
		assertEquals(90.0, ((Number) segment.getParameter("baseYawDeg")).doubleValue(), 1e-6);
	}
}
