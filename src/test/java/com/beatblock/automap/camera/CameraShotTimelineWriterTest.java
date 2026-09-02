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
			CameraShotTransition.SMOOTH,
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
		assertEquals("SMOOTH", segment.getParameter(CameraSegmentSemantics.KEY_TRANSITION));
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
			CameraShotTransition.SMOOTH,
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
			CameraShotTransition.SMOOTH,
			CameraShotEasing.EASE_OUT,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			0
		);

		assertEquals(0, CameraShotTimelineWriter.write(timeline, List.of(shot)));
	}
}
