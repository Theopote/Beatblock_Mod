package com.beatblock.audio.beatmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatEventTest {

	@Test
	void clampsEnergyAndDefaultsBandKey() {
		BeatEvent event = new BeatEvent(1000, (String) null, 2f, null, 0, 1, 1);
		assertEquals("low", event.bandKey());
		assertEquals(1f, event.energy(), 1e-6f);
		assertEquals(AnchorType.ARRIVE, event.anchor());
	}

	@Test
	void bandMapsKnownKeysToFrequencyBands() {
		assertEquals(FrequencyBand.LOW, new BeatEvent(0, "kick", 0.5f, AnchorType.ARRIVE, 0, 0, 0).band());
		assertEquals(FrequencyBand.HIGH, new BeatEvent(0, "hihat", 0.5f, AnchorType.ARRIVE, 0, 0, 0).band());
		assertEquals(FrequencyBand.MID, new BeatEvent(0, "snare", 0.5f, AnchorType.ARRIVE, 0, 0, 0).band());
	}

	@Test
	void legacyConstructorUsesBandNameAsKey() {
		BeatEvent event = new BeatEvent(500, FrequencyBand.HIGH, 0.4f, AnchorType.DEPART, 2, 1, 3);
		assertEquals("high", event.bandKey());
		assertEquals(FrequencyBand.HIGH, event.band());
	}

	@Test
	void compareToOrdersByTimeMs() {
		BeatEvent earlier = new BeatEvent(100, "kick", 0.5f, AnchorType.ARRIVE, 0, 0, 0);
		BeatEvent later = new BeatEvent(200, "kick", 0.5f, AnchorType.ARRIVE, 1, 0, 1);
		assertTrue(earlier.compareTo(later) < 0);
	}
}
