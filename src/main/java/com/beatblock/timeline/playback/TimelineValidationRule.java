package com.beatblock.timeline.playback;

/** One independently composable timeline validation domain. */
@FunctionalInterface
public interface TimelineValidationRule {
	void validate(TimelineCompileContext context, DiagnosticCollector diagnostics);
}