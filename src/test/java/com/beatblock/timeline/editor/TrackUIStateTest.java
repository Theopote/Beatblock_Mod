package com.beatblock.timeline.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackUIStateTest {

	@Test
	void toggleCollapsedFlipsState() {
		TrackUIState state = new TrackUIState();
		assertFalse(state.isCollapsed());
		state.toggleCollapsed();
		assertTrue(state.isCollapsed());
		state.toggleCollapsed();
		assertFalse(state.isCollapsed());
	}

	@Test
	void clampsHeightToMinimum() {
		TrackUIState state = new TrackUIState();
		state.setHeightPx(2f);
		assertEquals(8f, state.getHeightPx(), 1e-6f);
	}
}
