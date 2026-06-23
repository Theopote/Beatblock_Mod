package com.beatblock.audio;

/**
 * 音频分析进度回调：{@code step} 为阶段标识（如 {@code DEPENDENCY_INSTALL}），{@code percent} 为 0–100。
 */
@FunctionalInterface
public interface AnalysisProgressCallback {
	void onProgress(String step, int percent);
}
