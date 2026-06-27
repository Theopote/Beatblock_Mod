package com.beatblock.ui.presenter;

import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.audio.IAudioAnalyzer;
import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import com.beatblock.runtime.BeatBlockContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
		assertNull(presenter.pythonRuntimeSummary());
		assertNull(presenter.runtimeHealthSnapshot());
		assertNull(presenter.backendAnalyzer());
	}

	@Test
	void setUseDemucsNoOpsWhenAnalyzerMissing() {
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(
			() -> BeatBlockContext.builder().build()
		);
		presenter.setUseDemucs(true);
		assertFalse(presenter.isUseDemucs());
	}

	@Test
	void exposesRuntimeDiagnosticsFromInjectedService() {
		AudioAnalysisService service = new AudioAnalysisService();
		BeatBlockContext context = BeatBlockContext.builder()
			.externalAudioAnalyzer(service)
			.build();
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(() -> context);

		assertTrue(presenter.isAnalyzerAvailable());
		assertNotNull(presenter.pythonRuntimeSummary());
		PythonEnvironmentDiagnostics.RuntimeHealthSnapshot health = presenter.runtimeHealthSnapshot();
		assertNotNull(health);
		IAudioAnalyzer backend = presenter.backendAnalyzer();
		assertNotNull(backend);
		assertEquals(0, presenter.activeAnalysisCount());
	}
}
