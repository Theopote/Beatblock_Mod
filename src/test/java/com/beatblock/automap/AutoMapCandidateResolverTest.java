package com.beatblock.automap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoMapCandidateResolverTest {

	private static AutoMapCandidate candidate(double time, String feature, float energy) {
		return candidate(time, feature, energy, 0.0);
	}

	private static AutoMapCandidate candidate(double time, String feature, float energy, double minGap) {
		AutoMapRule rule = new AutoMapRule(feature, 0.1f, "anim", 0.5, false, 1f, minGap, null);
		return new AutoMapCandidate(time, feature, feature, energy, rule);
	}

	@Test
	void allowsDifferentFeaturesAtSameTime() {
		var resolved = AutoMapCandidateResolver.resolve(List.of(
			candidate(1.0, "low", 0.8f),
			candidate(1.0, "high", 0.7f)
		), 0.08);

		assertEquals(2, resolved.size());
	}

	@Test
	void enforcesMinGapPerFeatureNotGloballyAcrossTracks() {
		var resolved = AutoMapCandidateResolver.resolve(List.of(
			candidate(1.0, "low", 0.8f),
			candidate(1.05, "mid", 0.8f),
			candidate(59.0, "low", 0.9f)
		), 0.08);

		assertEquals(3, resolved.size());
	}

	@Test
	void perFeatureMinGapAllowsDenserHighBandThanLowBand() {
		var resolved = AutoMapCandidateResolver.resolve(List.of(
			candidate(1.00, "low", 0.8f, 0.12),
			candidate(1.05, "low", 0.8f, 0.12),
			candidate(1.00, "high", 0.8f, 0.04),
			candidate(1.04, "high", 0.8f, 0.04)
		), 0.08);

		assertEquals(3, resolved.size());
	}

	@Test
	void dropsEventsWithinSameFeatureMinGap() {
		var resolved = AutoMapCandidateResolver.resolve(List.of(
			candidate(1.0, "mid", 0.8f),
			candidate(1.05, "mid", 0.8f),
			candidate(1.20, "mid", 0.8f)
		), 0.08);

		assertEquals(2, resolved.size());
		assertEquals(1.0, resolved.get(0).timeSeconds(), 1e-6);
		assertEquals(1.20, resolved.get(1).timeSeconds(), 1e-6);
	}
}
