package com.beatblock.timeline.generation;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistancePacingTest {

	@Test
	void accumulatesGapByBlockDistance() {
		List<BlockPos> blocks = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(3, 64, 0),
			new BlockPos(3, 67, 0)
		);
		List<Double> times = DistancePacing.computeBlockTimestamps(blocks, 1.0, true, 0.1, 0.05);
		assertEquals(3, times.size());
		assertEquals(1.0, times.get(0), 1e-6);
		assertEquals(1.3, times.get(1), 1e-6);
		assertEquals(1.6, times.get(2), 1e-6);
	}

	@Test
	void enforcesMinGapWhenBlocksAdjacent() {
		List<BlockPos> blocks = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		);
		List<Double> times = DistancePacing.computeBlockTimestamps(blocks, 0.0, true, 0.01, 0.2);
		assertEquals(0.0, times.get(0), 1e-6);
		assertEquals(0.2, times.get(1), 1e-6);
	}

	@Test
	void plannerUsesDistanceModeForParkourPath() {
		var event = new com.beatblock.timeline.TimelineAnimationEvent(
			"parkour", 0.0, 0.3, "BlockTap", "stage", 1f,
			Map.of(
				"dispatchModel", "STEP",
				"stepStartMode", "IMMEDIATE",
				"pacingMode", "DISTANCE",
				"distancePaceSecondsPerBlock", 0.1,
				"distancePaceMinGapSeconds", 0.05
			)
		);
		List<BlockPos> blocks = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(4, 64, 0),
			new BlockPos(4, 64, 3)
		);
		var planned = StepSequencePlanner.plan(blocks, event, new double[] {0.5, 1.0}, 120);
		assertEquals(3, planned.size());
		assertEquals(0.0, planned.get(0).startTimeSeconds(), 1e-6);
		assertEquals(0.4, planned.get(1).startTimeSeconds(), 1e-6);
		assertTrue(planned.get(2).startTimeSeconds() > planned.get(1).startTimeSeconds());
		assertEquals(0.7, planned.get(2).startTimeSeconds(), 1e-6);
	}

	@Test
	void defersFirstBlockByMinGapWhenNotImmediate() {
		List<BlockPos> blocks = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		);
		List<Double> times = DistancePacing.computeBlockTimestamps(blocks, 1.0, false, 0.1, 0.25);
		assertEquals(2, times.size());
		assertEquals(1.25, times.get(0), 1e-6);
		assertEquals(1.5, times.get(1), 1e-6);
	}

	@Test
	void singleBlockWhenNotImmediateUsesLeadInGapOnly() {
		List<BlockPos> blocks = List.of(new BlockPos(0, 64, 0));
		List<Double> times = DistancePacing.computeBlockTimestamps(blocks, 2.0, false, 0.1, 0.3);
		assertEquals(1, times.size());
		assertEquals(2.3, times.get(0), 1e-6);
	}

	@Test
	void computeTimestampsReturnsAnchorWhenOrderedBlocksEmpty() {
		var request = new PacingRequest(1, 4.0, true, new double[0], 120, 0.5, List.of(), 0.1, 0.05);
		List<Double> times = PacingStrategy.distance().computeTimestamps(request);
		assertEquals(1, times.size());
		assertEquals(4.0, times.get(0), 1e-9);
	}
}
