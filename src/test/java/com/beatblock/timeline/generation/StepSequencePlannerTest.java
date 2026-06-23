package com.beatblock.timeline.generation;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepSequencePlannerTest {

	@Test
	void fixedIntervalDefersFirstSlotWhenNotImmediate() {
		var times = FixedIntervalPacing.INSTANCE.computeTimestamps(new PacingRequest(
			2, 1.0, false, new double[0], 120, 0.5));
		assertEquals(2, times.size());
		assertEquals(1.5, times.get(0), 1e-6);
		assertEquals(2.0, times.get(1), 1e-6);
	}

	@Test
	void beatGridDefersFirstSlotToNextBeatAfterAnchor() {
		var times = BeatGridPacing.INSTANCE.computeTimestamps(new PacingRequest(
			2, 1.0, false, new double[] {1.5, 2.0, 2.5}, 120, 0.5));
		assertEquals(2, times.size());
		assertEquals(1.5, times.get(0), 1e-6);
		assertEquals(2.0, times.get(1), 1e-6);
	}

	@Test
	void plansOneBlockPerBeatSlot() {
		var event = new com.beatblock.timeline.TimelineAnimationEvent(
			"e1", 1.0, 0.5, "BlockJump", "stage", 1f,
			Map.of("blocksPerBeat", 1, "stepStartMode", "IMMEDIATE", "dispatchModel", "STEP")
		);
		List<BlockPos> blocks = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0),
			new BlockPos(2, 64, 0)
		);
		var planned = StepSequencePlanner.plan(blocks, event, new double[] {1.0, 1.5, 2.0}, 120);
		assertEquals(3, planned.size());
		assertEquals(1.0, planned.get(0).startTimeSeconds(), 1e-6);
		assertEquals(1.5, planned.get(1).startTimeSeconds(), 1e-6);
		assertEquals(2.0, planned.get(2).startTimeSeconds(), 1e-6);
	}

	@Test
	void beatGridFallsBackToFixedIntervalWhenBeatsExhausted() {
		var times = BeatGridPacing.INSTANCE.computeTimestamps(new PacingRequest(
			3, 0.0, true, new double[] {0.0}, 120, 0.5));
		assertEquals(3, times.size());
		assertTrue(times.get(1) > times.get(0));
	}

	@Test
	void resolveAnchorTimeUsesNextBeatWhenNotImmediate() {
		double anchor = StepSequencePlanner.resolveAnchorTime(
			1.2, false, new double[] {0.5, 1.5, 2.0});
		assertEquals(1.5, anchor, 1e-9);
	}

	@Test
	void resolveAnchorTimeUsesEventTimeWhenImmediate() {
		assertEquals(1.0, StepSequencePlanner.resolveAnchorTime(1.0, true, new double[] {1.5}), 1e-9);
	}

	@Test
	void plansByDistancePacingMode() {
		var event = new com.beatblock.timeline.TimelineAnimationEvent(
			"e1", 1.0, 0.5, "BlockJump", "stage", 1f,
			Map.of(
				"pacingMode", "DISTANCE",
				"stepStartMode", "IMMEDIATE",
				"distancePaceSecondsPerBlock", 0.1,
				"distancePaceMinGapSeconds", 0.05
			)
		);
		List<BlockPos> blocks = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(3, 64, 0)
		);
		var planned = StepSequencePlanner.plan(blocks, event, new double[0], 120);

		assertEquals(2, planned.size());
		assertEquals(1.0, planned.get(0).startTimeSeconds(), 1e-6);
		assertTrue(planned.get(1).startTimeSeconds() > planned.get(0).startTimeSeconds());
	}
}
