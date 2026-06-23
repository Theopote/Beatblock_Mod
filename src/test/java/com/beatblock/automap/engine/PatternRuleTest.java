package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternRuleTest {

	@Test
	void defaultsNullFields() {
		PatternRule rule = new PatternRule(null, null);
		assertEquals(RhythmType.KICK, rule.getTrigger());
		assertEquals("jump", rule.getAnimationTypeId());
	}

	@Test
	void preservesConfiguredValues() {
		PatternRule rule = new PatternRule(RhythmType.HIHAT, "pulse");
		assertEquals(RhythmType.HIHAT, rule.getTrigger());
		assertEquals("pulse", rule.getAnimationTypeId());
	}
}
