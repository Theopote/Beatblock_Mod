package com.beatblock.automap.camera;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraContinuityPlannerTest {

	@Test
	void abuttingOppositeScreenDirectionUsesCut() {
		CameraShot northish = shot(0.0, 2.0, CameraSubject.worldPosition(0, 64, 0), CameraShotMovement.HOLD);
		CameraShot southish = shot(2.0, 2.0, CameraSubject.worldPosition(0, 64, 0), CameraShotMovement.PULL_OUT);

		CameraContinuityJump jump = CameraContinuityPlanner.evaluate(northish, southish);
		assertTrue(jump.oppositeScreenDirection() || jump.yawDeltaDeg() >= 90.0);
		assertEquals(0.0, jump.gapSeconds(), 1e-9);

		List<CameraShot> planned = CameraContinuityPlanner.plan(List.of(northish, southish));
		assertEquals(CameraShotTransition.CUT, planned.get(0).transition());
		assertEquals(CameraShotTransition.CUT, planned.get(1).transition());
	}

	@Test
	void softSameSubjectAbuttingUsesSmoothMove() {
		CameraShot a = shot(0.0, 2.0, CameraSubject.worldPosition(0, 64, 0), CameraShotMovement.HOLD);
		CameraShot b = shot(2.0, 2.0, CameraSubject.worldPosition(0, 64, 0), CameraShotMovement.HOLD);

		List<CameraShot> planned = CameraContinuityPlanner.plan(List.of(a, b));
		assertEquals(CameraShotTransition.SMOOTH_MOVE, planned.get(1).transition());
	}

	@Test
	void subjectChangeUsesDissolve() {
		CameraShot a = shot(0.0, 2.0, CameraSubject.worldPosition(0, 64, 0), CameraShotMovement.HOLD);
		CameraShot b = shot(2.05, 2.0, CameraSubject.worldPosition(12, 64, 0), CameraShotMovement.HOLD);

		List<CameraShot> planned = CameraContinuityPlanner.plan(List.of(a, b));
		assertEquals(CameraShotTransition.DISSOLVE, planned.get(1).transition());
	}

	@Test
	void largeGapAlwaysCuts() {
		CameraShot a = shot(0.0, 1.0, CameraSubject.worldPosition(0, 64, 0), CameraShotMovement.HOLD);
		CameraShot b = shot(3.0, 1.0, CameraSubject.worldPosition(0, 64, 0), CameraShotMovement.HOLD);

		List<CameraShot> planned = CameraContinuityPlanner.plan(List.of(a, b));
		assertEquals(CameraShotTransition.CUT, planned.get(1).transition());
	}

	@Test
	void legacySmoothAliasMapsToSmoothMove() {
		assertEquals(CameraShotTransition.SMOOTH_MOVE, CameraShotTransition.parse("SMOOTH"));
		assertEquals(0.12, CameraShotTransition.WHIP.blendSeconds(), 1e-9);
	}

	private static CameraShot shot(
		double start,
		double duration,
		CameraSubject subject,
		CameraShotMovement movement
	) {
		return new CameraShot(
			start,
			duration,
			subject,
			CameraShotFraming.MEDIUM,
			movement,
			subject,
			CameraShotTransition.CUT,
			CameraShotEasing.SMOOTH,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			0
		);
	}
}
