package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.cache.BeatmapAnalysisCache;
import com.beatblock.audio.python.PythonAudioAnalyzer;
import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 在后台线程调度音频分析任务，解析进度输出，完成后加载 Beatmap。
 * <p>
 * Python 环境探测与错误归类见 {@link PythonEnvironmentDiagnostics}；
 * 缓存路径与兼容性见 {@link BeatmapAnalysisCache}；
 * 分析后端见 {@link IAudioAnalyzer}（默认 {@link PythonAudioAnalyzer}）。
 */
public final class AudioAnalysisService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioAnalysisService.class);

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-analyzer");
		t.setDaemon(true);
		return t;
	});
	private final ExecutorService summaryExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-python-summary");
		t.setDaemon(true);
		return t;
	});
	private final PythonEnvironmentDiagnostics pythonDiagnostics;
	private final IAudioAnalyzer analyzer;

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
		this.analyzer = new PythonAudioAnalyzer(pythonDiagnostics);
	}

	AudioAnalysisService(IAudioAnalyzer analyzer, PythonEnvironmentDiagnostics pythonDiagnostics) {
		this.analyzer = analyzer;
		this.pythonDiagnostics = pythonDiagnostics;
	}

	public IAudioAnalyzer getAnalyzer() {
		return analyzer;
	}

	public boolean isUseDemucs() { return useDemucs; }
	public void setUseDemucs(boolean useDemucs) { this.useDemucs = useDemucs; }

	public Future<?> analyze(
		Path audioPath,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError
	) {
		return analyze(audioPath, onProgress, onComplete, onError, null, null);
	}

	public Future<?> analyze(
		Path audioPath,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Runnable onStarted
	) {
		return analyze(audioPath, onProgress, onComplete, onError, null, onStarted);
	}

	public Future<?> analyze(
		Path audioPath,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		Runnable onStarted
	) {
		return analyze(audioPath, onProgress, onComplete, onError, onSummary, onStarted, useDemucs);
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
		AnalysisCancelControl control = new AnalysisCancelControl();
		Future<?> delegate = executor.submit(() -> {
			if (onStarted != null) {
				onStarted.run();
			}
			analyzer.analyze(
				audioPath,
				AnalysisOptions.withDemucs(requestedDemucs),
				onProgress,
				onComplete,
				onError,
				onSummary,
				control
			);
		});
		return wrapCancelableFuture(delegate, control);
	}

	public void shutdown() {
		executor.shutdownNow();
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

	private Future<?> wrapCancelableFuture(
		Future<?> delegate,
		AnalysisCancelControl control
	) {
		return new Future<>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				control.cancelRunningProcess();
				return delegate.cancel(true);
			}

			@Override
			public boolean isCancelled() {
				return delegate.isCancelled();
			}

			@Override
			public boolean isDone() {
				return delegate.isDone();
			}

			@Override
			public Object get() throws InterruptedException, ExecutionException {
				return delegate.get();
			}

			@Override
			public Object get(long timeout, @NonNull TimeUnit unit)
				throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
				return delegate.get(timeout, unit);
			}
		};
	}
}
