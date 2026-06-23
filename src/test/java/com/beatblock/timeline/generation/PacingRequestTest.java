package com.beatblock.timeline.generation;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacingRequestTest {

	@Test
	void clampsNegativeSlotCountAndDerivesFixedIntervalFromBpm() {
		var request = new PacingRequest(-3, -1.0, true, null, 120, 0);
		assertEquals(0, request.slotCount());
		assertEquals(0.0, request.anchorTimeSeconds(), 1e-9);
		assertEquals(0.5, request.fixedIntervalSeconds(), 1e-9);
	}

	@Test
	void copiesReferenceBeatsDefensively() {
		double[] beats = {1.0, 2.0};
		var request = new PacingRequest(1, 0, true, beats, 120, 0.5);
		beats[0] = 99.0;
		assertEquals(1.0, request.referenceBeatTimesSeconds()[0], 1e-9);
	}

	@Test
	void distanceConstructorDefaultsBlockPacingFields() {
		var request = new PacingRequest(
			2, 1.0, true, new double[0], 120, 0.5,
			java.util.List.of(new BlockPos(0, 64, 0)), 0, -1);
		assertEquals(DistancePacing.DEFAULT_SECONDS_PER_BLOCK_UNIT, request.secondsPerBlockUnit(), 1e-9);
		assertEquals(DistancePacing.DEFAULT_MIN_GAP_SECONDS, request.minGapSeconds(), 1e-9);
	}
}
