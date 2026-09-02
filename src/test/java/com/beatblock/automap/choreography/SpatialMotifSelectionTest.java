package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialMotifSelectionTest {

	@Test
	void mapsSectionTypesToExpandedMotifPalette() {
		assertEquals(SpatialMotifId.GATHER, SpatialMotifSelection.forSection(SectionType.INTRO));
		assertEquals(SpatialMotifId.SWEEP, SpatialMotifSelection.forSection(SectionType.PRE_CHORUS));
		assertEquals(SpatialMotifId.EXPLODE, SpatialMotifSelection.forSection(SectionType.DROP));
		assertEquals(SpatialMotifId.CHASE, SpatialMotifSelection.forSection(SectionType.BRIDGE));
		assertEquals(SpatialMotifId.RIPPLE, SpatialMotifSelection.forSection(SectionType.BREAK));
	}
}
