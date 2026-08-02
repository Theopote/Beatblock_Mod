package com.beatblock.timeline.playback;

import com.beatblock.timeline.Timeline;

final class TimelineStructureValidator implements TimelineValidationRule {
	@Override
	public void validate(TimelineCompileContext context, DiagnosticCollector diagnostics) {
		Timeline document = context.document();
		double duration = document.getDurationSeconds();
		if (!Double.isFinite(duration)) {
			diagnostics.add(TimelineDiagnostic.error("non_finite_timeline_duration",
				"Timeline duration is not finite: " + duration, null, Double.NaN));
		}

		Object bpmMetadata = document.getMetadata("bpm");
		double bpm = document.getBpm();
		boolean unsetDefault = bpmMetadata == null && bpm == 0.0;
		if (!unsetDefault && (!Double.isFinite(bpm) || bpm <= 0)) {
			diagnostics.add(TimelineDiagnostic.error("invalid_bpm",
				"Timeline has invalid effective BPM value: " + bpm, null, Double.NaN));
		} else if (bpmMetadata instanceof Number number) {
			double metadataBpm = number.doubleValue();
			if (Double.isFinite(metadataBpm) && Double.isFinite(bpm) && metadataBpm > 0 && bpm > 0
				&& Math.abs(metadataBpm - bpm) > 1e-9) {
				diagnostics.add(TimelineDiagnostic.warning(TimelineValidator.RULE_BPM_METADATA_MISMATCH,
					"Timeline BPM metadata (" + metadataBpm + ") differs from effective BPM (" + bpm + ")",
					null, Double.NaN));
			}
		}

		for (var track : document.getTracks()) {
			if (track == null) continue;
			for (var clip : track.getClips()) {
				if (clip == null) continue;
				double start = clip.getStartTimeSeconds();
				double end = clip.getEndTimeSeconds();
				if (!Double.isFinite(start) || !Double.isFinite(end) || start > end) {
					diagnostics.add(TimelineDiagnostic.error("invalid_clip_range",
						"Track \"" + track.getName() + "\" has invalid clip range: [" + start + ", " + end + "]",
						null, Double.NaN));
				}
				if (Timeline.TRACK_ID_CAMERA.equals(track.getId()) || Timeline.TRACK_ID_GLOBAL.equals(track.getId())) continue;
				for (var event : clip.getEvents()) {
					if (event != null && !Double.isFinite(event.getTimeSeconds())) {
						diagnostics.add(TimelineDiagnostic.error("non_finite_event_time",
							"Event in track \"" + track.getName() + "\" has non-finite time: " + event.getTimeSeconds(),
							event.getId(), event.getTimeSeconds()));
					}
				}
			}
		}

		if (document.getMarkers() != null) {
			for (var marker : document.getMarkers()) {
				if (marker != null && !Double.isFinite(marker.getTimeSeconds())) {
					diagnostics.add(TimelineDiagnostic.error("non_finite_marker_time",
						"Marker \"" + marker.getName() + "\" has non-finite time: " + marker.getTimeSeconds(),
						marker.getId(), marker.getTimeSeconds()));
				}
			}
		}
	}
}