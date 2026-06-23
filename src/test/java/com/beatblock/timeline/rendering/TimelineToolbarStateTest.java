package com.beatblock.timeline.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineToolbarStateTest {

	@Test
	void hasLoopRangeWhenOutGreaterThanIn() {
		TimelineToolbarState state = new TimelineToolbarState();
		state.setLoopInSeconds(2.0);
		state.setLoopOutSeconds(5.0);
		assertTrue(state.hasLoopRange());
	}

	@Test
	void hasLoopRangeFalseWhenOutNotAfterIn() {
		TimelineToolbarState state = new TimelineToolbarState();
		state.setLoopInSeconds(3.0);
		state.setLoopOutSeconds(3.0);
		assertFalse(state.hasLoopRange());
	}

	@Test
	void clearLoopRangeResetsBounds() {
		TimelineToolbarState state = new TimelineToolbarState();
		state.setLoop(true);
		state.setLoopInSeconds(1.0);
		state.setLoopOutSeconds(4.0);
		state.clearLoopRange();
		assertTrue(state.isLoop());
		assertEquals(0.0, state.getLoopInSeconds(), 1e-9);
		assertEquals(0.0, state.getLoopOutSeconds(), 1e-9);
		assertFalse(state.hasLoopRange());
	}

	@Test
	void clampsNegativeLoopBoundsToZero() {
		TimelineToolbarState state = new TimelineToolbarState();
		state.setLoopInSeconds(-2.0);
		state.setLoopOutSeconds(-1.0);
		assertEquals(0.0, state.getLoopInSeconds(), 1e-9);
		assertEquals(0.0, state.getLoopOutSeconds(), 1e-9);
	}
}
