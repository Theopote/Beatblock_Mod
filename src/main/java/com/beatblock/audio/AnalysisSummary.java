package com.beatblock.audio;

/**
 * Python analyze.py 分析完成后的摘要信息。
 */
public record AnalysisSummary(
	float bpm,
	int beatCount,
	int sectionCount,
	long durationMs,
	String separationMode,
	String cacheSource
) {}
