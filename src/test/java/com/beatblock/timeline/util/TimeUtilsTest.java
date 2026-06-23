package com.beatblock.timeline.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilsTest {

	@Test
	void usesCoarserGridForWideVisibleRange() {
		assertEquals(5.0, TimeUtils.gridStep(0, 40, 1f), 1e-9);
		assertEquals(2.0, TimeUtils.gridStep(0, 15, 1f), 1e-9);
		assertEquals(1.0, TimeUtils.gridStep(0, 5, 1f), 1e-9);
		assertEquals(0.5, TimeUtils.gridStep(0, 1.5, 1f), 1e-9);
	}

	@Test
	void handlesZeroRangeSafely() {
		assertEquals(0.5, TimeUtils.gridStep(10, 10, 1f), 1e-9);
	}
}
