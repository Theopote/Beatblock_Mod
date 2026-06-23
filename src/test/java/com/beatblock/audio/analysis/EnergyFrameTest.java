package com.beatblock.audio.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyFrameTest {

	@Test
	void clampsNegativeEnergyToZero() {
		EnergyFrame frame = new EnergyFrame(2.5, -0.3f);
		assertEquals(0f, frame.getEnergy(), 1e-6f);
		assertEquals(2.5, frame.getTimeSeconds(), 1e-9);
	}

	@Test
	void preservesPositiveEnergy() {
		EnergyFrame frame = new EnergyFrame(0.75, 0.85f);
		assertEquals(0.85f, frame.getEnergy(), 1e-6f);
	}
}
