package com.beatblock.timeline.generation;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistancePacingGeometryTest {

	@Test
	void blockDistanceUsesEuclideanMetric() {
		BlockPos a = new BlockPos(0, 0, 0);
		BlockPos b = new BlockPos(3, 4, 0);
		assertEquals(5.0, DistancePacing.blockDistance(a, b), 1e-9);
	}

	@Test
	void emptyOrderedBlocksReturnsAnchorOnly() {
		var request = new PacingRequest(
			1, 2.5, true, new double[0], 120, 0.5,
			java.util.List.of(), 0.1, 0.05);
		assertEquals(2.5, PacingStrategy.distance().computeTimestamps(request).getFirst(), 1e-9);
	}
}
