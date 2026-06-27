package com.beatblock.audio;

import org.jspecify.annotations.NonNull;

/**
 * 音频分析进度回调：{@code step} 为阶段标识（如 {@code DEPENDENCY_INSTALL}），{@code percent} 为 0–100。
 */
@FunctionalInterface
public interface AnalysisProgressCallback {
	void onProgress(@NonNull String step, int percent);
}
