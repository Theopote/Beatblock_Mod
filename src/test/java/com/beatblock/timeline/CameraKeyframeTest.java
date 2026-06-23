package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraKeyframeTest {

	@Test
	void clampsNegativeTimeToZero() {
		assertEquals(0.0, new CameraKeyframe(-3.0).getTimeSeconds(), 1e-9);
	}

	@Test
	void preservesNonNegativeTime() {
		assertEquals(4.25, new CameraKeyframe(4.25).getTimeSeconds(), 1e-9);
	}
}
