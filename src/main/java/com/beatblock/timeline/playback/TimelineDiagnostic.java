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

	/**
	 * Returns true if this diagnostic is a fatal/blocking error that cannot be bypassed.
	 */
	public boolean isFatal() {
		return "unsupported_payload".equals(ruleId)
			|| "unsupported_parameter_type".equals(ruleId)
			|| "invalid_global_payload".equals(ruleId)
			|| "non_finite_event_time".equals(ruleId)
			|| "non_finite_event_duration".equals(ruleId)
			|| "non_finite_timeline_duration".equals(ruleId)
			|| "non_finite_camera_time".equals(ruleId)
			|| "non_finite_marker_time".equals(ruleId)
			|| "non_finite_global_time".equals(ruleId)
			|| "non_finite_beat_time".equals(ruleId)
			|| "invalid_clip_range".equals(ruleId)
			|| "invalid_bpm".equals(ruleId)
			|| "compiler_internal_error".equals(ruleId)
			|| "duplicate_event_id".equals(ruleId)
			|| "null_timeline".equals(ruleId);
	}
}

