package com.beatblock.audio.python;

import com.beatblock.audio.AnalysisCancelControl;
import com.beatblock.audio.AnalysisOptions;
import com.beatblock.audio.AnalysisProgressCallback;
import com.beatblock.audio.AnalysisSummary;
import com.beatblock.audio.AnalyzerInstaller;
import com.beatblock.audio.IAudioAnalyzer;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapReader;
import com.beatblock.audio.cache.BeatmapAnalysisCache;
import com.beatblock.audio.process.AnalyzerProcessIo;
import com.beatblock.audio.process.ProcessIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

/**
 * 调用 Python analyze.py 完成音频分析（缓存、环境、依赖、进程通信）。
 */
public final class PythonAudioAnalyzer implements IAudioAnalyzer {

	private static final Logger LOGGER = LoggerFactory.getLogger(PythonAudioAnalyzer.class);

	private final PythonEnvironmentDiagnostics pythonDiagnostics;

	public PythonAudioAnalyzer(PythonEnvironmentDiagnostics pythonDiagnostics) {
		this.pythonDiagnostics = pythonDiagnostics;
	}

	@Override
	public String backendId() {
		return "python";
	}

	@Override
	public boolean isAvailable() {
		Path configDir = pythonDiagnostics.configDirOrNull();
		if (configDir == null) {
			return false;
		}
		String pythonExe = pythonDiagnostics.resolvePythonExe(configDir);
		return pythonExe != null && !pythonExe.isBlank();
	}

	@Override
	public void analyze(
		Path audioPath,
		AnalysisOptions options,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		AnalysisCancelControl control
	) {
		boolean useDemucs = options != null && options.useDemucs();
		runAnalysisInternal(audioPath, onProgress, onComplete, onError, onSummary, control, true, useDemucs);
	}

	private void runAnalysisInternal(
		Path audioPath,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		AnalysisCancelControl control,
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

		onProgress.onProgress("DEPENDENCY_INSTALL", 0);
		Path requirementsPath = scriptPath.getParent().resolve("requirements.txt");
		String dependencyError = pythonDiagnostics.ensurePythonDependencies(
			pythonExe, requirementsPath, control, onProgress, analysisUseDemucs);
		if (dependencyError != null) {
			if (allowDemucsFallback && analysisUseDemucs && PythonEnvironmentDiagnostics.looksLikeDemucsMissing(dependencyError)) {
				LOGGER.warn("BeatBlock AudioAnalysis: Demucs dependencies unavailable, fallback to basic mode path={} reason={}",
					audioPath.getFileName(), ProcessIo.sanitizeProcessOutput(dependencyError));
				onProgress.onProgress("DEMUCS_FALLBACK", 100);
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
		onProgress.onProgress("DEPENDENCY_INSTALL", 100);

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
				onProgress.onProgress("DEMUCS_FALLBACK", 100);
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
}
