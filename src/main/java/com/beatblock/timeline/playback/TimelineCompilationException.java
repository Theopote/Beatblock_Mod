package com.beatblock.timeline.playback;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Thrown when a timeline cannot be compiled safely.
 */
public final class TimelineCompilationException extends RuntimeException {
	private final @Nullable TimelineValidationReport report;

	public TimelineCompilationException(TimelineValidationReport report) {
		super(buildSummary(report));
		this.report = Objects.requireNonNull(report, "report");
	}

	public TimelineCompilationException(TimelineValidationReport report, Throwable cause) {
		super(buildSummary(report), cause);
		this.report = Objects.requireNonNull(report, "report");
	}

	public TimelineCompilationException(String message) {
		super(message);
		this.report = null;
	}

	public TimelineCompilationException(String message, Throwable cause) {
		super(message, cause);
		this.report = null;
	}

	/** Full validation context when compilation was rejected by validation. */
	public @Nullable TimelineValidationReport report() {
		return report;
	}

	private static String buildSummary(TimelineValidationReport report) {
		Objects.requireNonNull(report, "report");
		String details = report.diagnostics().stream()
			.filter(diagnostic -> diagnostic.severity() == TimelineDiagnosticSeverity.ERROR)
			.map(TimelineCompilationException::formatDiagnostic)
			.collect(Collectors.joining("; "));
		return details.isEmpty() ? "Timeline validation failed" : details;
	}

	private static String formatDiagnostic(TimelineDiagnostic diagnostic) {
		StringBuilder result = new StringBuilder(diagnostic.message())
			.append(" [ruleId=").append(diagnostic.ruleId());
		if (diagnostic.eventId() != null) {
			result.append(", eventId=").append(diagnostic.eventId());
		}
		if (diagnostic.hasTime()) {
			result.append(", timeSeconds=").append(diagnostic.timeSeconds());
		}
		if (diagnostic.trackHint() != null) {
			result.append(", track=").append(diagnostic.trackHint());
		}
		if (diagnostic.sourceLocation() != null) {
			TimelineSourceLocation source = diagnostic.sourceLocation();
			result.append(", sourceIndex=").append(source.sourceIndex());
			if (!source.trackId().isBlank()) result.append(", sourceTrack=").append(source.trackId());
			if (!source.clipId().isBlank()) result.append(", sourceClip=").append(source.clipId());
		}
		return result.append(']').toString();
	}
}