package com.beatblock.ui.presenter;

import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.audio.IAudioAnalyzer;
import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import com.beatblock.runtime.BeatBlockContext;

import java.util.function.Supplier;

/**
 * 音频解析面板：Demucs 开关与 Python 运行时状态（经 {@link BeatBlockContext} 注入）。
 */
public final class AudioAnalysisPanelPresenter {

	private final Supplier<BeatBlockContext> context;

	public AudioAnalysisPanelPresenter(Supplier<BeatBlockContext> context) {
		this.context = context;
	}

	public AudioAnalysisService externalAnalyzer() {
		return context.get().externalAudioAnalyzer();
	}

	public boolean isAnalyzerAvailable() {
		return externalAnalyzer() != null;
	}

	public boolean isUseDemucs() {
		AudioAnalysisService analyzer = externalAnalyzer();
		return analyzer != null && analyzer.isUseDemucs();
	}

	public void setUseDemucs(boolean enabled) {
		AudioAnalysisService analyzer = externalAnalyzer();
		if (analyzer != null) {
			analyzer.setUseDemucs(enabled);
		}
	}

	public String pythonRuntimeSummary() {
		AudioAnalysisService analyzer = externalAnalyzer();
		if (analyzer == null) {
			return null;
		}
		return analyzer.getPythonRuntimeSummary();
	}

	public PythonEnvironmentDiagnostics.RuntimeHealthSnapshot runtimeHealthSnapshot() {
		AudioAnalysisService analyzer = externalAnalyzer();
		return analyzer != null ? analyzer.getRuntimeHealthSnapshot() : null;
	}

	public IAudioAnalyzer backendAnalyzer() {
		AudioAnalysisService analyzer = externalAnalyzer();
		return analyzer != null ? analyzer.getAnalyzer() : null;
	}

	public int activeAnalysisCount() {
		AudioAnalysisService analyzer = externalAnalyzer();
		return analyzer != null ? analyzer.getActiveAnalysisCount() : 0;
	}
}
