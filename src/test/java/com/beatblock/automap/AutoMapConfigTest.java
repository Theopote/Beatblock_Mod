package com.beatblock.automap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMapConfigTest {

	@Test
	void createDefaultProvidesLowMidHighRules() {
		AutoMapConfig config = AutoMapConfig.createDefault();
		assertEquals(3, config.getRules().size());
		assertEquals("low", config.getRules().get(0).getFeatureKey());
		assertEquals("mid", config.getRules().get(1).getFeatureKey());
		assertEquals("high", config.getRules().get(2).getFeatureKey());
		assertEquals(3f, config.getDefaultHeightMultiplier(), 1e-6f);
		assertEquals(0.08, config.getMinGapSeconds(), 1e-9);
	}

	@Test
	void clampsNegativeMinGapToZero() {
		AutoMapConfig config = new AutoMapConfig(null, 1f, -0.5);
		assertEquals(0.0, config.getMinGapSeconds(), 1e-9);
		assertTrue(config.getRules().isEmpty());
	}
}
