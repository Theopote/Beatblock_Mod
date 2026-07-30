package com.beatblock.audio;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/** Immutable scheduler view suitable for UI polling. */
public record AnalysisTaskSnapshot(
	long sequence,
	@Nullable String taskId,
	Path audioPath,
	AnalysisTaskState state,
	int queuePosition
) {}
