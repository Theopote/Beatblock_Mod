package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimelineAnimationActionModeTest {

	@Test
	void fromValueParsesKnownModes() {
		assertEquals(TimelineAnimationActionMode.BUILD, TimelineAnimationActionMode.fromValue("build"));
		assertEquals(TimelineAnimationActionMode.PLACE, TimelineAnimationActionMode.fromValue("PLACE"));
		assertEquals(TimelineAnimationActionMode.CLEAR, TimelineAnimationActionMode.fromValue("clear"));
	}

	@Test
	void fromValueDefaultsToAnimateForNullOrUnknown() {
		assertEquals(TimelineAnimationActionMode.ANIMATE, TimelineAnimationActionMode.fromValue(null));
		assertEquals(TimelineAnimationActionMode.ANIMATE, TimelineAnimationActionMode.fromValue("unknown"));
	}
}
