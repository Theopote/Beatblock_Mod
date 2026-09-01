package com.beatblock.automap.camera;

import com.beatblock.BeatBlock;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.test.WithBeatBlockContext;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class CameraSubjectResolverTest {

	@Test
	void resolvesRegisteredStageObject() {
		BlockAnimationEngine engine = BeatBlock.getContext().blockAnimationEngine();
		engine.getStageObjectSystem().register(
			StageObjectSystem.fromBlocks("tower", "Tower", List.of(new BlockPos(0, 64, 0))));

		CameraSubjectResolveResult result = CameraSubjectResolver.resolveResult(
			CameraSubject.stageObject("tower"), CameraSubjectRole.SUBJECT, engine, null);

		assertTrue(result.resolved());
		assertEquals(0.5, result.position().x, 1e-6);
		assertEquals(64.0, result.position().y, 1e-6);
		assertEquals(0.5, result.position().z, 1e-6);
	}

	@Test
	void missingStageObjectDoesNotFallBackToWorldOrigin() {
		BlockAnimationEngine engine = BeatBlock.getContext().blockAnimationEngine();

		CameraSubjectResolveResult result = CameraSubjectResolver.resolveResult(
			CameraSubject.stageObject("deleted-object"), CameraSubjectRole.SUBJECT, engine, null);

		assertFalse(result.resolved());
		assertEquals(CameraValidationRules.MISSING_CAMERA_SUBJECT, result.ruleId());
	}

	@Test
	void missingLookAtUsesDedicatedRule() {
		CameraSubjectResolveResult result = CameraSubjectResolver.resolveResult(
			CameraSubject.stageObject("gone"), CameraSubjectRole.LOOK_AT, null, null);

		assertFalse(result.resolved());
		assertEquals(CameraValidationRules.MISSING_CAMERA_LOOK_AT, result.ruleId());
	}
}
