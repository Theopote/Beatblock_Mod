package com.beatblock.timeline.playback;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One validation finding produced by {@link TimelineValidator}.
 *
 * @param ruleId     stable machine id (e.g. {@code missing_animation_preset})
 * @param severity   error / warning / info
 * @param message    human-readable text (already localized when produced from UI path)
 * @param eventId    related stage event id, if any
 * @param timeSeconds event time, or {@code NaN} when not applicable
 * @param trackHint  optional track / lane hint
 */
public record TimelineDiagnostic(
	String ruleId,
	TimelineDiagnosticSeverity severity,
	String message,
	@Nullable String eventId,
	double timeSeconds,
	@Nullable String trackHint
) {
	public TimelineDiagnostic {
		Objects.requireNonNull(ruleId, "ruleId");
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(message, "message");
		if (eventId != null && eventId.isBlank()) {
			eventId = null;
		}
		if (trackHint != null && trackHint.isBlank()) {
			trackHint = null;
		}
	}

	public static TimelineDiagnostic error(String ruleId, String message, @Nullable String eventId, double timeSeconds) {
		return new TimelineDiagnostic(ruleId, TimelineDiagnosticSeverity.ERROR, message, eventId, timeSeconds, null);
	}

	public static TimelineDiagnostic warning(String ruleId, String message, @Nullable String eventId, double timeSeconds) {
		return new TimelineDiagnostic(ruleId, TimelineDiagnosticSeverity.WARNING, message, eventId, timeSeconds, null);
	}

	public static TimelineDiagnostic info(String ruleId, String message) {
		return new TimelineDiagnostic(ruleId, TimelineDiagnosticSeverity.INFO, message, null, Double.NaN, null);
	}

	public boolean hasTime() {
		return !Double.isNaN(timeSeconds);
	}
}
