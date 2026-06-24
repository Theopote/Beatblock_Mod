package com.beatblock.ui.panels.audioanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioAnalysisPanelUiStateTest {

	@Test
	void panelHintExpiresAfterTtl() throws InterruptedException {
		AudioAnalysisPanelUiState state = new AudioAnalysisPanelUiState();
		state.setPanelHint("ok", false);
		assertTrue(state.hasPanelHint());

		Thread.sleep(5100L);
		state.prunePanelHint();

		assertNull(state.panelHintText());
		assertFalse(state.hasPanelHint());
	}

	@Test
	void detailExpandedToggles() {
		AudioAnalysisPanelUiState state = new AudioAnalysisPanelUiState();
		assertTrue(state.detailExpanded());
		state.toggleDetailExpanded();
		assertFalse(state.detailExpanded());
	}
}
