package com.beatblock.timeline.playback;

import java.util.List;

/**
 * Result of compiling a timeline.
 *
 * @param snapshot         the compiled timeline snapshot
 * @param report           the validation report generated at compile time
 * @param skippedEventIds  the list of event IDs skipped during compilation
 */
public record CompileResult(
	CompiledTimelineSnapshot snapshot,
	TimelineValidationReport report,
	List<String> skippedEventIds
) {
	public CompileResult {
		skippedEventIds = List.copyOf(skippedEventIds != null ? skippedEventIds : List.of());
	}
}
