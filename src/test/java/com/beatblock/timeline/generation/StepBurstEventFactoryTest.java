package com.beatblock.timeline.generation;

import com.beatblock.engine.GroupSpec;
import com.beatblock.engine.StageObject;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepBurstEventFactoryTest {

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
		StageObject target = new StageObject(
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
		assertFalse(StepBurstEventFactory.isStepDispatch(null));
		assertFalse(StepBurstEventFactory.isStepDispatch(Map.of("dispatchModel", "BURST")));
		assertTrue(StepBurstEventFactory.isStepDispatch(Map.of("dispatchModel", " step ")));
		assertTrue(StepBurstEventFactory.isStepDispatch(Map.of("dispatchModel", "Step")));
	}

	@Test
	void expandReturnsEmptyForNonStepOrMissingTarget() {
		var stepEvent = new TimelineAnimationEvent(
			"step-1", 0, 0.5, "BlockJump", "stage-a", 1f,
			Map.of("dispatchModel", "STEP"));
		assertTrue(StepBurstEventFactory.expand(stepEvent, null, new double[0], 120, null).isEmpty());
		assertTrue(StepBurstEventFactory.expand(
			new TimelineAnimationEvent("x", 0, 0.5, "BlockJump", "stage-a", 1f, Map.of("dispatchModel", "BURST")),
			new StageObject("stage-a", "A", List.of(new BlockPos(0, 64, 0)), Vec3d.ZERO, GroupSpec.manualSnapshot()),
			new double[0], 120, null
		).isEmpty());
	}
}
