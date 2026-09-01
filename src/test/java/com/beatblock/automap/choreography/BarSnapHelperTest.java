package com.beatblock.automap.choreography;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarSnapHelperTest {

	@Test
	void snapsNearBarStartWithinTolerance() {
		var musical = new ChoreographyPlan.MusicalStructure(
			List.of(
				new ChoreographyPlan.BarPlan(0.0, 2.0, 0, 0),
				new ChoreographyPlan.BarPlan(2.0, 4.0, 1, 0)
			),
			List.of(),
			List.of()
		);

		assertEquals(2.0, BarSnapHelper.snapToNearestBarStart(2.05, musical, 0.08), 1e-6);
		assertEquals(1.02, BarSnapHelper.snapToNearestBarStart(1.02, musical, 0.08), 1e-6);
	}

	@Test
	void leavesDistantTimesUnchanged() {
		var musical = new ChoreographyPlan.MusicalStructure(
			List.of(new ChoreographyPlan.BarPlan(0.0, 2.0, 0, 0)),
			List.of(),
			List.of()
		);

		assertEquals(1.5, BarSnapHelper.snapToNearestBarStart(1.5, musical, 0.08), 1e-6);
	}
}
