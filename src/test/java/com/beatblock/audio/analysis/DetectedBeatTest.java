package com.beatblock.audio.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DetectedBeatTest {

	@Test
	void clampsStrengthToUnitInterval() {
		DetectedBeat weak = new DetectedBeat(1.0, -0.2f);
		DetectedBeat strong = new DetectedBeat(2.0, 1.8f);
		assertEquals(0f, weak.getStrength(), 1e-6f);
		assertEquals(1f, strong.getStrength(), 1e-6f);
	}

	@Test
	void preservesTime() {
		assertEquals(3.25, new DetectedBeat(3.25, 0.5f).getTimeSeconds(), 1e-9);
	}
}
