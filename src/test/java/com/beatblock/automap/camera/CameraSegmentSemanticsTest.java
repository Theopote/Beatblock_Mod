package com.beatblock.automap.camera;

import com.beatblock.BeatBlock;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.test.WithBeatBlockContext;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithBeatBlockContext
class CameraSegmentSemanticsTest {

	@Test
	void encodesShotSemanticsForTimelineSegment() {
		BeatBlock.getContext().blockAnimationEngine().getStageObjectSystem().register(
			StageObjectSystem.fromBlocks("stage-b", "Stage B", List.of(new BlockPos(3, 64, 4))));

		CameraShot shot = new CameraShot(
			1.0,
			2.0,
			CameraSubject.worldPosition(10, 64, 20),
			CameraShotFraming.CLOSE,
			CameraShotMovement.HOLD,
			CameraSubject.animatedTarget("stage-b"),
			CameraShotTransition.DISSOLVE,
			CameraShotEasing.EASE_IN,
			CameraCollisionPolicy.CLIP_TO_BOUNDS,
			CameraShotBeatAlignment.none(),
			0
		);

		Map<String, Object> params = CameraSegmentSemantics.fromShot(shot);

		assertEquals("EASE_IN", params.get(CameraSegmentSemantics.KEY_EASE));
		assertEquals("DISSOLVE", params.get(CameraSegmentSemantics.KEY_TRANSITION));
		assertEquals("CLIP_TO_BOUNDS", params.get(CameraSegmentSemantics.KEY_COLLISION_POLICY));
		assertEquals("ANIMATED_TARGET", params.get(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_KIND));
		assertEquals("stage-b", params.get(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_REF));
		assertNotNull(params.get(CameraSegmentSemantics.KEY_BAKED_TARGET_X));
	}
}
