package com.beatblock.timeline.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicalDurationUnitTest {

	@Test
	void convertsBeatsAndBarsAt120Bpm() {
		double bpm = 120.0;
		assertEquals(2.0, MusicalDurationUnit.BEATS.toSeconds(4.0, bpm), 1e-9);
		assertEquals(4.0, MusicalDurationUnit.BEATS.fromSeconds(2.0, bpm), 1e-9);
		assertEquals(2.0, MusicalDurationUnit.BARS.toSeconds(1.0, bpm), 1e-9);
		assertEquals(1.0, MusicalDurationUnit.BARS.fromSeconds(2.0, bpm), 1e-9);
	}

	@Test
	void fallsBackWhenBpmMissing() {
		assertEquals(
			MusicalDurationUnit.BEATS.toSeconds(4.0, 120.0),
			MusicalDurationUnit.BEATS.toSeconds(4.0, 0.0),
			1e-9
		);
	}

	@Test
	void secondsPassthrough() {
		assertEquals(3.5, MusicalDurationUnit.SECONDS.toSeconds(3.5, 90.0), 1e-9);
		assertEquals(3.5, MusicalDurationUnit.SECONDS.fromSeconds(3.5, 90.0), 1e-9);
	}
}
