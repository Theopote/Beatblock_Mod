package com.beatblock.audio.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeatGridTest {

	@Test
	void generatesBeatTimesFromBpmAndDuration() {
		BeatGrid grid = new BeatGrid(120f, 2.0);
		assertEquals(120f, grid.getBpm(), 1e-6f);
		assertEquals(0.0, grid.getFirstBeatTime(), 1e-9);
		assertEquals(5, grid.getBeatTimes().size());
		assertEquals(0.0, grid.getBeatTimes().get(0), 1e-9);
		assertEquals(0.5, grid.getBeatTimes().get(1), 1e-9);
		assertEquals(2.0, grid.getBeatTimes().get(4), 1e-9);
	}

	@Test
	void honorsFirstBeatOffset() {
		BeatGrid grid = new BeatGrid(60f, 3.0, 0.25);
		assertEquals(0.25, grid.getFirstBeatTime(), 1e-9);
		assertEquals(0.25, grid.getBeatTimes().getFirst(), 1e-9);
		assertEquals(1.25, grid.getBeatTimes().get(1), 1e-9);
	}

	@Test
	void subdivisionsDeriveFromBeatDuration() {
		BeatGrid grid = new BeatGrid(120f, 1.0);
		assertEquals(0.5, grid.getBeatDuration(), 1e-9);
		assertEquals(0.25, grid.getEighthDuration(), 1e-9);
		assertEquals(0.125, grid.getSixteenthDuration(), 1e-9);
	}

	@Test
	void clampsInvalidBpmToAtLeastOne() {
		BeatGrid grid = new BeatGrid(0f, 1.0);
		assertEquals(1f, grid.getBpm(), 1e-6f);
		assertEquals(60.0, grid.getBeatDuration(), 1e-9);
	}
}
