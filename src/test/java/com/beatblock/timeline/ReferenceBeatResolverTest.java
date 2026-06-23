package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceBeatResolverTest {

	@Test
	void prefersKickOverOtherFeatureTracks() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("snare", new FeatureEvent(2.0, 0.5f));
		timeline.addFeatureEvent("kick", new FeatureEvent(0.5, 0.5f));
		timeline.addFeatureEvent("kick", new FeatureEvent(1.5, 0.5f));

		assertEquals("kick", ReferenceBeatResolver.describePrimaryRhythmKey(timeline));
		assertArrayEquals(new double[] {0.5, 1.5}, ReferenceBeatResolver.resolveBeatTimesSeconds(timeline), 1e-9);
	}

	@Test
	void fallsBackToLowThenDrums() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("hihat", new FeatureEvent(3.0, 0.5f));
		timeline.addFeatureEvent("low", new FeatureEvent(1.0, 0.5f));

		assertEquals("low", ReferenceBeatResolver.describePrimaryRhythmKey(timeline));
		assertArrayEquals(new double[] {1.0}, ReferenceBeatResolver.resolveBeatTimesSeconds(timeline), 1e-9);
	}

	@Test
	void mergesNearDuplicateBeatTimes() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.5f));
		timeline.addFeatureEvent("kick", new FeatureEvent(1.00001, 0.5f));
		timeline.addFeatureEvent("kick", new FeatureEvent(2.0, 0.5f));

		assertEquals(2, ReferenceBeatResolver.resolveBeatTimesSeconds(timeline).length);
	}

	@Test
	void returnsEmptyWhenNoFeatureTracks() {
		Timeline timeline = Timeline.createDefault();
		assertEquals(0, ReferenceBeatResolver.resolveBeatTimesSeconds(timeline).length);
		assertEquals("", ReferenceBeatResolver.describePrimaryRhythmKey(timeline));
	}
}
