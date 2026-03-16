package com.beatblock.audio.beatmap;

/**
 * Beatmap 元信息，对应 JSON 中的 meta 字段。
 */
public record BeatmapMeta(
	String  sourceFile,
	long    durationMs,
	double  bpm,
	double  bpmConfidence,
	String  timeSignature,
	int     sampleRate,
	String  generatedAt,
	String  analyzerVersion
) {}

