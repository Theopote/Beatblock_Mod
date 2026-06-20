package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapReader;
import com.beatblock.audio.cache.BeatmapAnalysisCache;
import com.beatblock.audio.process.AnalyzerProcessIo;
import com.beatblock.audio.process.ProcessIo;
import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import com.beatblock.audio.python.PythonProbeInfo;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 在后台线程调用 Python analyze.py，解析进度输出，完成后加载 Beatmap。
 * <p>
 * Python 环境探测与错误归类见 {@link PythonEnvironmentDiagnostics}；
 * 缓存路径与兼容性见 {@link BeatmapAnalysisCache}。
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
	private final PythonEnvironmentDiagnostics pythonDiagnostics = new PythonEnvironmentDiagnostics();

	private volatile String cachedPythonSummary = "Python: 检测中...";
	private volatile long nextPythonSummaryRefreshAtMs;
	private volatile boolean pythonSummaryRefreshInFlight;
	private volatile PythonEnvironmentDiagnostics.RuntimeHealthSnapshot cachedRuntimeHealthSnapshot =
		PythonEnvironmentDiagnostics.RuntimeHealthSnapshot.empty();
	private volatile long nextRuntimeHealthRefreshAtMs;
	private final AtomicBoolean runtimeHealthRefreshInFlight = new AtomicBoolean();

	private volatile boolean useDemucs = true;

	public boolean isUseDemucs() { return useDemucs; }
	public void setUseDemucs(boolean useDemucs) { this.useDemucs = useDemucs; }

	public Future<?> analyze(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError
	) {
		return analyze(audioPath, onProgress, onComplete, onError, null, null);
	}

	public Future<?> analyze(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Runnable onStarted
	) {
		return analyze(audioPath, onProgress, onComplete, onError, null, onStarted);
	}

	public Future<?> analyze(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		Runnable onStarted
	) {
		return analyze(audioPath, onProgress, onComplete, onError, onSummary, onStarted, useDemucs);
	}

	public Future<?> analyze(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		Runnable onStarted,
		boolean requestedDemucs
	) {
		PythonEnvironmentDiagnostics.AnalysisControl control = new PythonEnvironmentDiagnostics.AnalysisControl();
		Future<?> delegate = executor.submit(() -> {
			if (onStarted != null) {
				onStarted.run();
			}
			runAnalysis(audioPath, onProgress, onComplete, onError, onSummary, control, requestedDemucs);
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

	private void runAnalysis(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		PythonEnvironmentDiagnostics.AnalysisControl control,
		boolean taskUseDemucs
	) {
		runAnalysisInternal(audioPath, onProgress, onComplete, onError, onSummary, control, true, taskUseDemucs);
	}

	private void runAnalysisInternal(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		PythonEnvironmentDiagnostics.AnalysisControl control,
		boolean allowDemucsFallback,
		boolean analysisUseDemucs
	) {
		Path scriptPath;
		Path outputDir;
		try {
			scriptPath = AnalyzerInstaller.ensureInstalled();
			outputDir = AnalyzerInstaller.getBeatmapOutputDir();
		} catch (AnalyzerInstaller.AnalyzerInstallException e) {
			onError.accept("脚本安装失败：" + e.getMessage());
			return;
		}

		Path beatmapPath = BeatmapAnalysisCache.buildBeatmapPath(outputDir, audioPath, analysisUseDemucs);

		if (Files.isRegularFile(beatmapPath)) {
			try {
				long fileSize = Files.size(beatmapPath);
				if (fileSize > 16) {
					Beatmap cached = BeatmapReader.read(beatmapPath);
					if (BeatmapAnalysisCache.isBeatmapVersionCompatible(cached, analysisUseDemucs, beatmapPath)) {
						LOGGER.info("BeatBlock AudioAnalysis: beatmap cache hit, skipping Python path={} beatmap={}",
							audioPath.getFileName(), beatmapPath.getFileName());
						if (onSummary != null && cached.meta != null) {
							onSummary.accept(new AnalysisSummary(
								(float) cached.meta.bpm(),
								cached.beats.size(),
								cached.sections.size(),
								cached.meta.durationMs(),
								cached.meta.hasStemSeparation() ? "demucs" : "basic",
								"beatmap-cache"
							));
						}
						onComplete.accept(cached);
						return;
					}
					LOGGER.info("BeatBlock AudioAnalysis: beatmap cache stale (analyzerVersion={}), re-analyzing path={}",
						cached.meta != null ? cached.meta.analyzerVersion() : "null",
						audioPath.getFileName());
					BeatmapAnalysisCache.cleanupDemucsStemArtifacts(cached, beatmapPath);
				}
			} catch (Exception e) {
				LOGGER.warn("BeatBlock AudioAnalysis: existing beatmap unreadable, re-analyzing path={} reason={}",
					audioPath.getFileName(), e.getMessage());
			}
		}

		Path configDir = outputDir.getParent();
		String pythonExe = pythonDiagnostics.resolvePythonExe(configDir);
		if (pythonExe == null) {
			onError.accept("""
				找不到 Python 解释器。
				请确认已安装 Python，或在 config/beatblock/python_path.txt 中指定完整路径。""");
			return;
		}
		if (control.isCancelled()) {
			onError.accept("分析被取消");
			return;
		}

		PythonProbeInfo pythonProbe = pythonDiagnostics.getProbeInfo(pythonExe);
		if (!pythonProbe.probeOk()) {
			onError.accept("无法探测 Python 运行环境：" + pythonProbe.detail());
			return;
		}
		if (!pythonProbe.isSupportedVersion()) {
			onError.accept("检测到 Python 版本过新（>=3.13），当前音频分析依赖在该版本上可能无预编译包。\n"
				+ "请在 config/beatblock/python_path.txt 指定 Python 3.10~3.12 路径后重试。");
			return;
		}
		if (!pythonProbe.hasPip()) {
			onError.accept("当前 Python 没有可用 pip。请先执行 python -m ensurepip --upgrade。\n"
				+ "当前解释器：" + pythonProbe.executablePath());
			return;
		}

		onProgress.accept("DEPENDENCY_INSTALL", 0);
		Path requirementsPath = scriptPath.getParent().resolve("requirements.txt");
		String dependencyError = pythonDiagnostics.ensurePythonDependencies(
			pythonExe, requirementsPath, control, onProgress, analysisUseDemucs);
		if (dependencyError != null) {
			if (allowDemucsFallback && analysisUseDemucs && PythonEnvironmentDiagnostics.looksLikeDemucsMissing(dependencyError)) {
				LOGGER.warn("BeatBlock AudioAnalysis: Demucs dependencies unavailable, fallback to basic mode path={} reason={}",
					audioPath.getFileName(), ProcessIo.sanitizeProcessOutput(dependencyError));
				onProgress.accept("DEMUCS_FALLBACK", 100);
				runAnalysisInternal(audioPath, onProgress, onComplete, onError, onSummary, control, false, false);
				return;
			}
			if (control.isCancelled()) {
				onError.accept("分析被取消");
				return;
			}
			onError.accept(dependencyError);
			return;
		}
		onProgress.accept("DEPENDENCY_INSTALL", 100);

		List<String> cmd = new ArrayList<>();
		cmd.add(pythonExe);
		cmd.add(scriptPath.toAbsolutePath().toString());
		cmd.add(audioPath.toAbsolutePath().toString());
		cmd.add(beatmapPath.toAbsolutePath().toString());
		cmd.add("--waveform");
		if (analysisUseDemucs) {
			cmd.add("--demucs");
		}

		Process process;
		try {
			process = new ProcessBuilder(cmd)
				.redirectErrorStream(false)
				.start();
			control.attachProcess(process);
		} catch (IOException e) {
			onError.accept("无法启动 Python：" + e.getMessage()
				+ "\n请确认 Python 已安装，路径：" + pythonExe);
			return;
		}

		AnalyzerProcessIo.StdoutParseResult stdoutResult;
		FutureTask<String> stdoutTask = new FutureTask<>(
			() -> AnalyzerProcessIo.consumeStdout(process.getInputStream(), onProgress)
		);
		FutureTask<String> stderrTask = new FutureTask<>(
			() -> ProcessIo.consumeLines(process.getErrorStream())
		);
		Thread stdoutThread = new Thread(stdoutTask, "beatblock-analyzer-stdout");
		stdoutThread.setDaemon(true);
		stdoutThread.start();
		Thread stderrThread = new Thread(stderrTask, "beatblock-analyzer-stderr");
		stderrThread.setDaemon(true);
		stderrThread.start();

		int exitCode;
		try {
			exitCode = process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			stdoutTask.cancel(true);
			stderrTask.cancel(true);
			onError.accept("分析被中断");
			return;
		} finally {
			control.clearProcess(process);
		}

		String stderrText;
		try {
			stdoutResult = AnalyzerProcessIo.parseStdoutResult(stdoutTask.get());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			onError.accept("读取 Python 输出时被中断");
			return;
		} catch (ExecutionException e) {
			onError.accept("读取 Python 输出失败：" + ProcessIo.rootMessage(e));
			return;
		}

		try {
			stderrText = stderrTask.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			onError.accept("读取 Python 错误输出时被中断");
			return;
		} catch (ExecutionException e) {
			stderrText = "读取 stderr 失败：" + ProcessIo.rootMessage(e);
		}

		if (exitCode != 0) {
			String detail = ProcessIo.sanitizeProcessOutput(stderrText);
			if (detail.isEmpty() && stdoutResult.errorText() != null && !stdoutResult.errorText().isBlank()) {
				detail = ProcessIo.sanitizeProcessOutput(stdoutResult.errorText());
			}
			if (detail.isEmpty() && stdoutResult.resultJson() != null && !stdoutResult.resultJson().isBlank()) {
				detail = ProcessIo.sanitizeProcessOutput(stdoutResult.resultJson());
			}
			String hint = PythonEnvironmentDiagnostics.explainPythonError(detail);
			if (allowDemucsFallback && analysisUseDemucs && PythonEnvironmentDiagnostics.looksLikeDemucsMissing(detail + "\n" + hint)) {
				LOGGER.warn("BeatBlock AudioAnalysis: Demucs runtime unavailable, fallback to basic mode path={} detail={}",
					audioPath.getFileName(), detail);
				onProgress.accept("DEMUCS_FALLBACK", 100);
				runAnalysisInternal(audioPath, onProgress, onComplete, onError, onSummary, control, false, false);
				return;
			}
			if (!detail.isEmpty()) {
				if (!hint.isEmpty()) {
					onError.accept("Python 分析脚本退出码：" + exitCode + "\n" + hint + "\n\n" + detail);
				} else {
					onError.accept("Python 分析脚本退出码：" + exitCode + "\n" + detail);
				}
			} else if (!hint.isEmpty()) {
				onError.accept("Python 分析脚本退出码：" + exitCode + "\n" + hint);
			} else {
				onError.accept("Python 分析脚本退出码：" + exitCode);
			}
			return;
		}

		if (onSummary != null && stdoutResult.resultJson() != null && !stdoutResult.resultJson().isBlank()) {
			AnalysisSummary summary = AnalyzerProcessIo.parseResultSummary(stdoutResult.resultJson());
			if (summary != null) {
				onSummary.accept(summary);
			}
		}

		try {
			Beatmap beatmap = BeatmapReader.read(beatmapPath);
			onComplete.accept(beatmap);
		} catch (Exception e) {
			onError.accept("读取 beatmap 文件失败：" + e.getMessage());
		}
	}

	private Future<?> wrapCancelableFuture(
		Future<?> delegate,
		PythonEnvironmentDiagnostics.AnalysisControl control
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

	@FunctionalInterface
	public interface BiConsumer<A, B> {
		void accept(A a, B b);
	}

	public record AnalysisSummary(
		float bpm,
		int beatCount,
		int sectionCount,
		long durationMs,
		String separationMode,
		String cacheSource
	) {}
}
