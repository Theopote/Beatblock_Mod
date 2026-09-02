package com.beatblock.timeline.playback;

import com.beatblock.timeline.ReferenceBeatResolver;

final class ReferenceBeatValidator implements TimelineValidationRule {
	@Override
	public void validate(TimelineCompileContext context, DiagnosticCollector diagnostics) {
		try {
			double[] beats = ReferenceBeatResolver.resolveBeatTimesSeconds(context.document());
			for (double beat : beats) {
				if (!Double.isFinite(beat)) {
					diagnostics.add(TimelineDiagnostic.error("non_finite_beat_time",
						"Reference beat time is not finite: " + beat, null, Double.NaN));
					break;
				}
			}
		} catch (RuntimeException error) {
			String message = error.getMessage();
			diagnostics.add(TimelineDiagnostic.error(TimelineValidator.RULE_INVALID_REFERENCE_BEAT_DATA,
				"Failed to resolve reference beats: "
					+ (message != null && !message.isBlank() ? message : error.getClass().getSimpleName()),
				null, Double.NaN));
		}
	}
}