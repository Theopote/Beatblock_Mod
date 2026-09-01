package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimingSnapResolverTest {

	private static final TimingSnapResolver.SnapContext CONTEXT = new TimingSnapResolver.SnapContext(
		new ChoreographyPlan.MusicalStructure(
			List.of(
				new ChoreographyPlan.BarPlan(0.0, 2.0, 0, 0),
				new ChoreographyPlan.BarPlan(2.0, 4.0, 1, 0)
			),
			List.of(new ChoreographyPlan.MusicalPhrasePlan(0.0, 4.0, 0, 0, 0.5, -1)),
			List.of(),
			List.of(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5)
		),
		List.of(new ChoreographyPlan.SectionPlan(0.0, 4.0, SectionType.DROP, "drop"))
	);

	@Test
	void snapsNearBarStartWithinTolerance() {
		assertEquals(2.0, TimingSnapResolver.snap(2.05, ChoreographyTimingSnap.BAR, CONTEXT), 1e-6);
		assertEquals(1.02, TimingSnapResolver.snap(1.02, ChoreographyTimingSnap.BAR, CONTEXT), 1e-6);
	}

	@Test
	void leavesDistantTimesUnchangedForBarSnap() {
		assertEquals(1.5, TimingSnapResolver.snap(1.5, ChoreographyTimingSnap.BAR, CONTEXT), 1e-6);
	}

	@Test
	void nonePreservesOriginalGroove() {
		assertEquals(1.47, TimingSnapResolver.snap(1.47, ChoreographyTimingSnap.NONE, CONTEXT), 1e-6);
	}

	@Test
	void beatSnapUsesBeatGrid() {
		assertEquals(1.5, TimingSnapResolver.snap(1.52, ChoreographyTimingSnap.BEAT, CONTEXT), 1e-6);
	}

	@Test
	void sectionSnapAlignsToSectionStart() {
		assertEquals(0.0, TimingSnapResolver.snap(0.05, ChoreographyTimingSnap.SECTION, CONTEXT), 1e-6);
	}
}
