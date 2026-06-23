package com.beatblock.timeline.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InteractionStateTest {

	@Test
	void defaultsToNoneModeAndEmptyGuides() {
		InteractionState state = new InteractionState();
		assertEquals(InteractionMode.NONE, state.getMode());
		assertEquals(0, state.getAlignmentGuideTimes().length);
	}

	@Test
	void setAlignmentGuideTimesCopiesArrayReference() {
		InteractionState state = new InteractionState();
		state.setAlignmentGuideTimes(new double[] {1.0, 2.5});
		assertArrayEquals(new double[] {1.0, 2.5}, state.getAlignmentGuideTimes(), 1e-9);
	}

	@Test
	void clearActiveResetsIdsAndGuides() {
		InteractionState state = new InteractionState();
		state.setMode(InteractionMode.DRAG_EVENT);
		state.setActiveEventId("e1");
		state.setActiveClipId("c1");
		state.setActiveTrackId("t1");
		state.setActiveMarkerId("m1");
		state.setAlignmentGuideTimes(new double[] {3.0});

		state.clearActive();

		assertNull(state.getActiveEventId());
		assertNull(state.getActiveClipId());
		assertNull(state.getActiveTrackId());
		assertNull(state.getActiveMarkerId());
		assertEquals(0, state.getAlignmentGuideTimes().length);
	}
}
