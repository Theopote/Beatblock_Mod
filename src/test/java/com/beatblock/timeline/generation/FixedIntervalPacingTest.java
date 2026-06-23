package com.beatblock.timeline.generation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedIntervalPacingTest {

	@Test
	void defersFirstSlotWhenNotStartingImmediately() {
		var request = new PacingRequest(3, 1.0, false, new double[0], 120, 0.5);
		List<Double> times = PacingStrategy.fixedInterval().computeTimestamps(request);
		assertEquals(3, times.size());
		assertEquals(1.5, times.get(0), 1e-9);
		assertEquals(2.0, times.get(1), 1e-9);
		assertEquals(2.5, times.get(2), 1e-9);
	}

	@Test
	void startsAtAnchorWhenImmediate() {
		var request = new PacingRequest(2, 3.0, true, new double[0], 120, 0.25);
		List<Double> times = PacingStrategy.fixedInterval().computeTimestamps(request);
		assertEquals(3.0, times.get(0), 1e-9);
		assertEquals(3.25, times.get(1), 1e-9);
	}

	@Test
	void returnsEmptyListForZeroSlotCount() {
		var request = new PacingRequest(0, 1.0, true, new double[0], 120, 0.5);
		assertTrue(PacingStrategy.fixedInterval().computeTimestamps(request).isEmpty());
	}
}
