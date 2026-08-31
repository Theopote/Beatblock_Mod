package com.beatblock.automap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AutoMapRuleTest {

	@Test
	void defaultsBlankFeatureKeyAndClampsEnergy() {
		AutoMapRule rule = new AutoMapRule("", -0.5f, null, 0, false, 1f);
		assertEquals("low", rule.getFeatureKey());
		assertEquals(0f, rule.getMinEnergy(), 1e-6f);
		assertEquals("bounce", rule.getAnimationTypeId());
		assertEquals(0.01, rule.getDurationSeconds(), 1e-9);
	}

	@Test
	void preservesConfiguredValues() {
		AutoMapRule rule = new AutoMapRule("kick", 0.35f, "BlockJump", 0.55, true, 1.2f);
		assertEquals("kick", rule.getFeatureKey());
		assertEquals(0.35f, rule.getMinEnergy(), 1e-6f);
		assertEquals("BlockJump", rule.getAnimationTypeId());
		assertEquals(0.55, rule.getDurationSeconds(), 1e-9);
		assertEquals(true, rule.isUseEnergyForHeight());
		assertEquals(1.2f, rule.getHeightMultiplier(), 1e-6f);
	}

	@Test
	void resolveMinGapUsesRuleOverrideWhenPositive() {
		AutoMapRule rule = new AutoMapRule("high", 0.1f, "pulse", 0.3, false, 1f, 0.04, null);
		assertEquals(0.04, rule.resolveMinGap(0.12), 1e-9);
	}

	@Test
	void resolveMinGapFallsBackToConfigDefault() {
		AutoMapRule rule = new AutoMapRule("low", 0.1f, "bounce", 0.5, true, 4f);
		assertEquals(0.12, rule.resolveMinGap(0.12), 1e-9);
	}
}
