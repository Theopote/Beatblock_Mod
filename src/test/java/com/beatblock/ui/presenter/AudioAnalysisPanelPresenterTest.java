package com.beatblock.ui.presenter;

import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.runtime.BeatBlockContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioAnalysisPanelPresenterTest {

	@Test
	void demucsToggleUsesInjectedAnalyzer() {
		AudioAnalysisService service = new AudioAnalysisService();
		service.setUseDemucs(true);
		BeatBlockContext context = BeatBlockContext.builder()
			.externalAudioAnalyzer(service)
			.build();
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(() -> context);

		assertTrue(presenter.isUseDemucs());
		presenter.setUseDemucs(false);
		assertFalse(presenter.isUseDemucs());
		assertFalse(service.isUseDemucs());
	}

	@Test
	void analyzerUnavailableWhenContextHasNoService() {
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(
			() -> BeatBlockContext.builder().build()
		);
		assertFalse(presenter.isAnalyzerAvailable());
		assertNull(presenter.externalAnalyzer());
		assertEquals(0, presenter.activeAnalysisCount());
	}
}
