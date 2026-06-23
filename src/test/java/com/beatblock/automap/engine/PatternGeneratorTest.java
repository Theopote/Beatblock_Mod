package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternGeneratorTest {

	@Test
	void lowComplexityUsesSparseGapAndHighThreshold() {
		assertEquals(0.25, PatternGenerator.getMinGapSeconds(Complexity.LOW), 1e-9);
		assertEquals(0.5f, PatternGenerator.getEnergyThreshold(Complexity.LOW), 1e-6f);
	}

	@Test
	void filterDropsWeakAndTooCloseEventsForLowComplexity() {
		List<RhythmEvent> input = List.of(
			new RhythmEvent(1.0, RhythmType.KICK, 0.8f),
			new RhythmEvent(1.1, RhythmType.SNARE, 0.8f),
			new RhythmEvent(1.4, RhythmType.HIHAT, 0.2f),
			new RhythmEvent(1.6, RhythmType.KICK, 0.7f)
		);

		List<RhythmEvent> filtered = PatternGenerator.filter(input, Complexity.LOW);

		assertEquals(2, filtered.size());
		assertEquals(1.0, filtered.get(0).getTimeSeconds(), 1e-6);
		assertEquals(1.6, filtered.get(1).getTimeSeconds(), 1e-6);
	}

	@Test
	void extremeComplexityKeepsMoreEvents() {
		List<RhythmEvent> input = List.of(
			new RhythmEvent(0.0, RhythmType.KICK, 0.1f),
			new RhythmEvent(0.05, RhythmType.SNARE, 0.12f),
			new RhythmEvent(0.10, RhythmType.HIHAT, 0.08f)
		);

		List<RhythmEvent> filtered = PatternGenerator.filter(input, Complexity.EXTREME);

		assertEquals(3, filtered.size());
	}
}
