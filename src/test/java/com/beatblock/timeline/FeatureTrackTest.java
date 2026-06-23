package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureTrackTest {

	@Test
	void addEventKeepsAscendingTimeOrder() {
		FeatureTrack track = new FeatureTrack("kick", "Kick");
		track.addEvent(new FeatureEvent(2.0, 0.8f));
		track.addEvent(new FeatureEvent(0.5, 0.3f));
		track.addEvent(new FeatureEvent(1.0, 0.5f));

		assertEquals(3, track.size());
		assertEquals(0.5, track.getEvents().get(0).getTimeSeconds(), 1e-9);
		assertEquals(1.0, track.getEvents().get(1).getTimeSeconds(), 1e-9);
		assertEquals(2.0, track.getEvents().get(2).getTimeSeconds(), 1e-9);
	}

	@Test
	void nullLabelFallsBackToKey() {
		FeatureTrack track = new FeatureTrack("snare", null);
		assertEquals("snare", track.getKey());
		assertEquals("snare", track.getLabel());
	}

	@Test
	void clearRemovesAllEvents() {
		FeatureTrack track = new FeatureTrack("hihat", "Hi-Hat");
		track.addEvent(new FeatureEvent(1.0, 0.5f));
		track.clear();
		assertTrue(track.getEvents().isEmpty());
		assertEquals(0, track.size());
	}
}
