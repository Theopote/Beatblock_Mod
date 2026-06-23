package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 外部音频分析后端（Python、未来 Java 原生或远程 API 等）。
 * <p>
 * 实现应在调用方线程上<strong>同步</strong>执行；异步调度由 {@link AudioAnalysisService} 负责。
 */
public interface IAudioAnalyzer {

	String backendId();

	boolean isAvailable();

	/**
	 * @param onSummary 可为 {@code null}
	 */
	void analyze(
		Path audioPath,
		AnalysisOptions options,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		AnalysisCancelControl control
	);
}
