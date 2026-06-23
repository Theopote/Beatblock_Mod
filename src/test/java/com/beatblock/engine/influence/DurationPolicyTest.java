package com.beatblock.engine.influence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationPolicyTest {

	@Test
	void fullDurationUsesSingleEntryRatio() {
		DurationPolicy policy = DurationPolicy.fullDuration();
		assertEquals(1f, policy.entryRatio(), 1e-6);
		assertEquals(0f, policy.idleRatio(), 1e-6);
		assertEquals(0f, policy.exitRatio(), 1e-6);
	}

	@Test
	void clampsRatiosToUnitInterval() {
		DurationPolicy policy = new DurationPolicy(2f, -1f, 0.5f);
		assertEquals(1f, policy.entryRatio(), 1e-6);
		assertEquals(0f, policy.idleRatio(), 1e-6);
		assertEquals(0.5f, policy.exitRatio(), 1e-6);
	}
}
