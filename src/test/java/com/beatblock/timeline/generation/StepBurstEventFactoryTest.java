package com.beatblock.timeline.generation;

import com.beatblock.engine.GroupSpec;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepBurstEventFactoryTest {

	private static final Set<String> STEP_ONLY_PARAM_KEYS = Set.of(
		"blocksPerBeat",
		"stepStartMode",
		"stepCompletionMode",
		"pacingMode",
		"distancePaceSecondsPerBlock",
		"distancePaceMinGapSeconds",
		"cameraAdaptiveStep",
		"cameraFrustumGating",
		"cameraEdgePriority",
		"usePhaseAnimation",
		"entryDurationPercent",
		"idleDurationPercent",
		"exitDurationPercent",
		"cameraNearDistance",
		"cameraFarDistance",
		"cameraNearScale",
		"cameraFarScale"
	);

	@Test
	void expandsStepIntoBurstEventsWithoutStepParams() {
		var stepEvent = new TimelineAnimationEvent(
			"step-1",
			1.0,
			0.5,
			"BlockJump",
			"stage-a",
			0.8f,
			Map.of(
				"dispatchModel", "STEP",
				"blocksPerBeat", 1,
				"stepStartMode", "IMMEDIATE",
				"cameraEdgePriority", 0.5
			)
		);
		RuntimeStageObject target = new RuntimeStageObject(
			"stage-a",
			"Stage A",
			List.of(
				new BlockPos(0, 64, 0),
				new BlockPos(1, 64, 0),
				new BlockPos(2, 64, 0)
			),
			Vec3d.ZERO,
			GroupSpec.manualSnapshot()
		);

		List<TimelineAnimationEvent> burst = StepBurstEventFactory.expand(
			stepEvent, target, new double[] {1.0, 1.5, 2.0}, 120.0, Vec3d.ZERO);

		assertEquals(3, burst.size());
		assertEquals(1.0, burst.get(0).getTimeSeconds(), 1e-6);
		assertEquals(1.5, burst.get(1).getTimeSeconds(), 1e-6);
		assertEquals(2.0, burst.get(2).getTimeSeconds(), 1e-6);
		for (TimelineAnimationEvent event : burst) {
			assertFalse(StepBurstEventFactory.isStepDispatch(event.getParameters()));
			assertEquals("BURST", event.getParameters().get("dispatchModel"));
			assertNotNull(StepBurstEventFactory.readSingleBlockPos(event.getParameters()));
			assertEquals("step-1", event.getParameters().get("bakedFromStepEventId"));
		}
	}

	@Test
	void readSingleBlockPosReturnsNullWhenMissing() {
		assertNull(StepBurstEventFactory.readSingleBlockPos(Map.of()));
		assertEquals(
			new BlockPos(3, 64, 5),
			StepBurstEventFactory.readSingleBlockPos(Map.of(
				"singleBlockX", 3,
				"singleBlockY", 64,
				"singleBlockZ", 5
			))
		);
	}

	@Test
	void isStepDispatchRecognizesStepModelCaseInsensitively() {
		assertFalse(StepBurstEventFactory.isStepDispatch((Map<String, Object>) null));
		assertFalse(StepBurstEventFactory.isStepDispatch((TimelineAnimationEvent) null));
		assertFalse(StepBurstEventFactory.isStepDispatch(Map.of("dispatchModel", "BURST")));
		assertTrue(StepBurstEventFactory.isStepDispatch(Map.of("dispatchModel", " step ")));
		assertTrue(StepBurstEventFactory.isStepDispatch(Map.of("dispatchModel", "Step")));
		assertTrue(StepBurstEventFactory.isStepDispatch(new TimelineAnimationEvent(
			"s", 0, 0.5, "pulse", "t", 1f, Map.of("dispatchModel", "STEP"))));
	}

	@Test
	void burstParamsFromStepStripsAllStepOnlyFields() {
		Map<String, Object> stepParams = new HashMap<>();
		stepParams.put("actionMode", "ANIMATE");
		stepParams.put("animationType", "BlockJump");
		stepParams.put("targetObject", "stage-a");
		stepParams.put("energy", 0.8f);
		stepParams.put("durationSeconds", 0.5);
		stepParams.put("dispatchModel", "STEP");
		stepParams.put("blocksPerBeat", 2);
		stepParams.put("stepStartMode", "IMMEDIATE");
		stepParams.put("stepCompletionMode", "KEEP");
		stepParams.put("pacingMode", "BEAT_GRID");
		stepParams.put("cameraAdaptiveStep", true);
		stepParams.put("cameraFrustumGating", true);
		stepParams.put("cameraEdgePriority", 0.5);
		stepParams.put("usePhaseAnimation", true);
		stepParams.put("entryDurationPercent", 30.0);
		stepParams.put("idleDurationPercent", 40.0);
		stepParams.put("exitDurationPercent", 30.0);
		stepParams.put("cameraNearDistance", 8.0);
		stepParams.put("cameraFarDistance", 48.0);
		stepParams.put("cameraNearScale", 0.6);
		stepParams.put("cameraFarScale", 1.5);

		Map<String, Object> burst = StepBurstEventFactory.burstParamsFromStep(
			stepParams, new BlockPos(3, 64, 5), "step-source");

		assertEquals("BURST", burst.get("dispatchModel"));
		for (String key : STEP_ONLY_PARAM_KEYS) {
			assertFalse(burst.containsKey(key), "BURST params must not retain STEP-only key: " + key);
		}
		assertEquals(3, burst.get("singleBlockX"));
		assertEquals(64, burst.get("singleBlockY"));
		assertEquals(5, burst.get("singleBlockZ"));
		assertEquals("step-source", burst.get("bakedFromStepEventId"));
	}

	@Test
	void expandStripsStepOnlyFieldsFromBakedBurstEvents() {
		Map<String, Object> params = new HashMap<>();
		params.put("dispatchModel", "STEP");
		params.put("blocksPerBeat", 1);
		params.put("stepStartMode", "IMMEDIATE");
		params.put("cameraAdaptiveStep", true);
		params.put("usePhaseAnimation", true);
		params.put("entryDurationPercent", 25.0);
		params.put("idleDurationPercent", 50.0);
		params.put("exitDurationPercent", 25.0);
		params.put("cameraNearDistance", 8.0);
		params.put("cameraFarDistance", 48.0);
		params.put("cameraNearScale", 0.5);
		params.put("cameraFarScale", 2.0);
		var stepEvent = new TimelineAnimationEvent(
			"step-1",
			0.0,
			0.5,
			"BlockJump",
			"stage-a",
			1f,
			params
		);
		RuntimeStageObject target = new RuntimeStageObject(
			"stage-a",
			"Stage A",
			List.of(new BlockPos(0, 64, 2), new BlockPos(0, 64, 40)),
			Vec3d.ZERO,
			GroupSpec.manualSnapshot()
		);

		List<TimelineAnimationEvent> burst = StepBurstEventFactory.expand(
			stepEvent, target, new double[] {0.0, 1.0}, 120.0, Vec3d.ZERO);

		assertEquals(2, burst.size());
		for (TimelineAnimationEvent event : burst) {
			for (String key : STEP_ONLY_PARAM_KEYS) {
				assertFalse(event.getParameters().containsKey(key),
					"Baked BURST must not retain STEP-only key: " + key);
			}
		}
	}

	@Test
	void expandReturnsEmptyForNonStepOrMissingTarget() {
		var stepEvent = new TimelineAnimationEvent(
			"step-1", 0, 0.5, "BlockJump", "stage-a", 1f,
			Map.of("dispatchModel", "STEP"));
		assertTrue(StepBurstEventFactory.expand(stepEvent, null, new double[0], 120, null).isEmpty());
		assertTrue(StepBurstEventFactory.expand(
			new TimelineAnimationEvent("x", 0, 0.5, "BlockJump", "stage-a", 1f, Map.of("dispatchModel", "BURST")),
			new RuntimeStageObject("stage-a", "A", List.of(new BlockPos(0, 64, 0)), Vec3d.ZERO, GroupSpec.manualSnapshot()),
			new double[0], 120, null
		).isEmpty());
	}
}
