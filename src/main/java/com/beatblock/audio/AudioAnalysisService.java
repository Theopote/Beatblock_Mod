package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.cache.BeatmapAnalysisCache;
import com.beatblock.audio.python.PythonAudioAnalyzer;
import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 音频分析对外入口：任务调度委托 {@link AudioAnalysisOrchestrator}，
 * Python 运行时健康检查由本类缓存刷新。
 */
public final class AudioAnalysisService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioAnalysisService.class);

	private final PythonEnvironmentDiagnostics pythonDiagnostics;
	private final AudioAnalysisOrchestrator orchestrator;

	private final ExecutorService summaryExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-python-summary");
		t.setDaemon(true);
		return t;
	});

	private volatile String cachedPythonSummary = "Python: 检测中...";
	private volatile long nextPythonSummaryRefreshAtMs;
	private volatile boolean pythonSummaryRefreshInFlight;
	private volatile PythonEnvironmentDiagnostics.RuntimeHealthSnapshot cachedRuntimeHealthSnapshot =
		PythonEnvironmentDiagnostics.RuntimeHealthSnapshot.empty();
	private volatile long nextRuntimeHealthRefreshAtMs;
	private final AtomicBoolean runtimeHealthRefreshInFlight = new AtomicBoolean();

	private volatile boolean useDemucs = true;

	public AudioAnalysisService() {
		this(new PythonEnvironmentDiagnostics());
	}

	public AudioAnalysisService(PythonEnvironmentDiagnostics pythonDiagnostics) {
		this.pythonDiagnostics = pythonDiagnostics;
		this.orchestrator = new AudioAnalysisOrchestrator(new PythonAudioAnalyzer(pythonDiagnostics));
	}

	AudioAnalysisService(IAudioAnalyzer analyzer, PythonEnvironmentDiagnostics pythonDiagnostics) {
		this.pythonDiagnostics = pythonDiagnostics;
		this.orchestrator = new AudioAnalysisOrchestrator(analyzer);
	}

	AudioAnalysisService(AudioAnalysisOrchestrator orchestrator, PythonEnvironmentDiagnostics pythonDiagnostics) {
		this.orchestrator = orchestrator;
		this.pythonDiagnostics = pythonDiagnostics;
	}

	public IAudioAnalyzer getAnalyzer() {
		return orchestrator.getAnalyzer();
	}

	public boolean isUseDemucs() { return useDemucs; }
	public void setUseDemucs(boolean useDemucs) { this.useDemucs = useDemucs; }

	public Future<?> analyze(
		Path audioPath,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError
	) {
		return submitAnalysis(null, audioPath, onProgress, onComplete, onError, null, null, useDemucs);
	}

	public Future<?> analyze(
		Path audioPath,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Runnable onStarted
	) {
		return submitAnalysis(null, audioPath, onProgress, onComplete, onError, null, onStarted, useDemucs);
	}

	public Future<?> analyze(
		Path audioPath,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		Runnable onStarted
	) {
		return submitAnalysis(null, audioPath, onProgress, onComplete, onError, onSummary, onStarted, useDemucs);
	}

	public Future<?> analyze(
		Path audioPath,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		Runnable onStarted,
		boolean requestedDemucs
	) {
		return submitAnalysis(null, audioPath, onProgress, onComplete, onError, onSummary, onStarted, requestedDemucs);
	}

	public Future<?> analyze(
		String taskId,
		Path audioPath,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		Runnable onStarted,
		boolean requestedDemucs
	) {
		return submitAnalysis(taskId, audioPath, onProgress, onComplete, onError, onSummary, onStarted, requestedDemucs);
	}

	private Future<?> submitAnalysis(
		String taskId,
		Path audioPath,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		Runnable onStarted,
		boolean requestedDemucs
	) {
		return orchestrator.submit(
			taskId,
			audioPath,
			AnalysisOptions.withDemucs(requestedDemucs),
			onProgress,
			onComplete,
			onError,
			onSummary,
			onStarted
		);
	}

	public boolean cancelAnalysis(String taskId) {
		return orchestrator.cancel(taskId);
	}

	public int getActiveAnalysisCount() {
		return orchestrator.activeTaskCount();
	}

	public void shutdown() {
		orchestrator.shutdown();
		summaryExecutor.shutdownNow();
	}

	public int clearBeatmapCacheForAudio(Path audioPath) {
		return BeatmapAnalysisCache.clearBeatmapCacheForAudio(audioPath);
	}

	public int clearAllAnalysisCacheForAudio(Path audioPath) {
		return BeatmapAnalysisCache.clearAllAnalysisCacheForAudio(audioPath);
	}

	public String getPythonRuntimeSummary() {
		long now = System.currentTimeMillis();
		if (cachedPythonSummary == null || cachedPythonSummary.isBlank()) {
			cachedPythonSummary = "Python: 检测中...";
		}
		if (now >= nextPythonSummaryRefreshAtMs) {
			triggerPythonSummaryRefreshAsync();
		}
		return cachedPythonSummary;
	}

	public PythonEnvironmentDiagnostics.RuntimeHealthSnapshot getRuntimeHealthSnapshot() {
		long now = System.currentTimeMillis();
		if (now >= nextRuntimeHealthRefreshAtMs) {
			triggerRuntimeHealthRefreshAsync();
		}
		return cachedRuntimeHealthSnapshot;
	}

	private void triggerPythonSummaryRefreshAsync() {
		if (pythonSummaryRefreshInFlight) return;
		synchronized (this) {
			if (pythonSummaryRefreshInFlight) return;
			pythonSummaryRefreshInFlight = true;
		}

		summaryExecutor.submit(() -> {
			try {
				Path configDir = pythonDiagnostics.configDirOrNull();
				String summary = configDir != null
					? pythonDiagnostics.probeRuntimeSummary(configDir)
					: "Python: 未检测到配置目录";
				if (!summary.isBlank()) {
					cachedPythonSummary = summary;
				}
			} catch (Exception e) {
				if (cachedPythonSummary == null || cachedPythonSummary.isBlank()) {
					cachedPythonSummary = "Python: 检测失败（" + e.getClass().getSimpleName() + "）";
				}
			} finally {
				nextPythonSummaryRefreshAtMs = System.currentTimeMillis() + 5000L;
				pythonSummaryRefreshInFlight = false;
			}
		});
	}

	private void triggerRuntimeHealthRefreshAsync() {
		if (!runtimeHealthRefreshInFlight.compareAndSet(false, true)) return;
		summaryExecutor.submit(() -> {
			try {
				Path configDir = pythonDiagnostics.configDirOrNull();
				cachedRuntimeHealthSnapshot = configDir != null
					? pythonDiagnostics.probeRuntimeHealth(configDir)
					: PythonEnvironmentDiagnostics.RuntimeHealthSnapshot.empty();
			} catch (Exception e) {
				LOGGER.debug("BeatBlock AudioAnalysis: runtime health probe failed: {}", e.toString());
			} finally {
				nextRuntimeHealthRefreshAtMs = System.currentTimeMillis() + 5000L;
				runtimeHealthRefreshInFlight.set(false);
			}
		});
	}
}
