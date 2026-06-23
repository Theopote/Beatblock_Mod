package com.beatblock.audio.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrequencyBandsTest {

	@Test
	void clampsNegativeBandValuesToZero() {
		FrequencyBands bands = new FrequencyBands(1.0, -0.1f, -0.2f, -0.3f);
		assertEquals(0f, bands.getLow(), 1e-6f);
		assertEquals(0f, bands.getMid(), 1e-6f);
		assertEquals(0f, bands.getHigh(), 1e-6f);
	}

	@Test
	void exposesStandardBandCutoffs() {
		assertTrue(FrequencyBands.LOW_HZ_MAX < FrequencyBands.MID_HZ_MAX);
		assertTrue(FrequencyBands.MID_HZ_MIN >= FrequencyBands.LOW_HZ_MAX);
		assertTrue(FrequencyBands.HIGH_HZ_MIN >= FrequencyBands.MID_HZ_MAX);
	}

	@Test
	void preservesTimeAndPositiveValues() {
		FrequencyBands bands = new FrequencyBands(0.5, 0.2f, 0.4f, 0.1f);
		assertEquals(0.5, bands.getTimeSeconds(), 1e-9);
		assertEquals(0.2f, bands.getLow(), 1e-6f);
	}
}
