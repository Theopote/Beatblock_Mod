package com.beatblock.timeline.playback;

import java.util.List;

/** Result of compiling a timeline, including every skipped source location. */
public record CompileResult(
	CompiledTimelineSnapshot snapshot,
	TimelineValidationReport report,
	List<String> skippedEventIds,
	List<TimelineSourceLocation> skippedLocations
) {
	public CompileResult {
		skippedEventIds = List.copyOf(skippedEventIds != null ? skippedEventIds : List.of());
		skippedLocations = List.copyOf(skippedLocations != null ? skippedLocations : List.of());
	}

	public CompileResult(CompiledTimelineSnapshot snapshot, TimelineValidationReport report,
		List<String> skippedEventIds) {
		this(snapshot, report, skippedEventIds, List.of());
	}
}