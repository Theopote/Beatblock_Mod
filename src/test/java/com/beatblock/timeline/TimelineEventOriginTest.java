package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimelineEventOriginTest {

	@Test
	void fromValueParsesKnownValues() {
		assertEquals(TimelineEventOrigin.MANUAL, TimelineEventOrigin.fromValue("MANUAL"));
		assertEquals(TimelineEventOrigin.AUTO_GENERATED, TimelineEventOrigin.fromValue("auto_generated"));
	}

	@Test
	void fromValueDefaultsToManualForNullBlankOrUnknown() {
		assertEquals(TimelineEventOrigin.MANUAL, TimelineEventOrigin.fromValue(null));
		assertEquals(TimelineEventOrigin.MANUAL, TimelineEventOrigin.fromValue(""));
		assertEquals(TimelineEventOrigin.MANUAL, TimelineEventOrigin.fromValue("  "));
		assertEquals(TimelineEventOrigin.MANUAL, TimelineEventOrigin.fromValue("draft"));
	}
}
