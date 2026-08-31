package com.beatblock.automap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMapConfigTest {

	@Test
	void createDefaultProvidesLowMidHighRulesWithPerFeatureMinGap() {
		AutoMapConfig config = AutoMapConfig.createDefault();
		assertEquals(3, config.getRules().size());
		assertEquals("low", config.getRules().get(0).getFeatureKey());
		assertEquals(0.12, config.getRules().get(0).getMinGapSeconds(), 1e-9);
		assertEquals("mid", config.getRules().get(1).getFeatureKey());
		assertEquals(0.08, config.getRules().get(1).getMinGapSeconds(), 1e-9);
		assertEquals("high", config.getRules().get(2).getFeatureKey());
		assertEquals(0.04, config.getRules().get(2).getMinGapSeconds(), 1e-9);
		assertEquals(3f, config.getDefaultHeightMultiplier(), 1e-6f);
		assertEquals(0.08, config.getMinGapSeconds(), 1e-9);
	}

	@Test
	void resolveTargetPrefersRuleThenConfigThenFallback() {
		AutoMapConfig config = AutoMapConfig.builder()
			.targetForFeature("low", "stage-a")
			.rule(new AutoMapRule("mid", 0.2f, "slide", 0.4, true, 3f, 0.08, "stage-mid-rule"))
			.build();

		assertEquals("stage-a", config.resolveTargetObjectId(null, "low", "fallback"));
		assertEquals("stage-mid-rule", config.resolveTargetObjectId(
			config.getRules().get(0), "mid", "fallback"));
		assertEquals("fallback", config.resolveTargetObjectId(null, "high", "fallback"));
	}

	@Test
	void clampsNegativeMinGapToZero() {
		AutoMapConfig config = new AutoMapConfig(null, 1f, -0.5);
		assertEquals(0.0, config.getMinGapSeconds(), 1e-9);
		assertTrue(config.getRules().isEmpty());
	}
}
