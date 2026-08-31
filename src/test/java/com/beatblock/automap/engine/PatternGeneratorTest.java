package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

		assertEquals(3, filtered.size());
		assertEquals(1.0, filtered.get(0).getTimeSeconds(), 1e-6);
		assertEquals(RhythmType.SNARE, filtered.get(1).getType());
		assertEquals(1.6, filtered.get(2).getTimeSeconds(), 1e-6);
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

	@Test
	void nullComplexityUsesMediumDefaults() {
		assertEquals(0.12, PatternGenerator.getMinGapSeconds(null), 1e-9);
		assertEquals(0.2f, PatternGenerator.getEnergyThreshold(null), 1e-6f);
	}

	@Test
	void filterReturnsEmptyForNullInput() {
		assertTrue(PatternGenerator.filter(null, Complexity.MEDIUM).isEmpty());
	}

	@Test
	void perFeatureMinGapAllowsKickAndSnareAtNearbyTimesForHighComplexity() {
		List<RhythmEvent> input = List.of(
			new RhythmEvent(1.0, RhythmType.KICK, 0.8f),
			new RhythmEvent(1.05, RhythmType.SNARE, 0.8f),
			new RhythmEvent(1.08, RhythmType.KICK, 0.8f)
		);

		List<RhythmEvent> filtered = PatternGenerator.filter(input, Complexity.HIGH);

		assertEquals(3, filtered.size());
	}

	@Test
	void settingsOverridePerFeatureMinGap() {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setComplexity(Complexity.MEDIUM);
		settings.setMinGapMid(0.2);

		List<RhythmEvent> input = List.of(
			new RhythmEvent(1.0, RhythmType.SNARE, 0.8f),
			new RhythmEvent(1.05, RhythmType.SNARE, 0.8f),
			new RhythmEvent(1.25, RhythmType.SNARE, 0.8f)
		);

		List<RhythmEvent> filtered = PatternGenerator.filter(input, settings);

		assertEquals(2, filtered.size());
		assertEquals(1.0, filtered.get(0).getTimeSeconds(), 1e-6);
		assertEquals(1.25, filtered.get(1).getTimeSeconds(), 1e-6);
	}

	@Test
	void featureMinGapsMatchAutoMapRuleDefaultsAtMediumComplexity() {
		PatternGenerator.FeatureMinGaps gaps = PatternGenerator.featureMinGaps(Complexity.MEDIUM);
		assertEquals(PatternGenerator.DEFAULT_LOW_GAP, gaps.low(), 1e-9);
		assertEquals(PatternGenerator.DEFAULT_MID_GAP, gaps.mid(), 1e-9);
		assertEquals(PatternGenerator.DEFAULT_HIGH_GAP, gaps.high(), 1e-9);
	}
}
