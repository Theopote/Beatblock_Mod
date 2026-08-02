package com.beatblock.timeline.playback;

import com.beatblock.timeline.Timeline;

final class CameraTrackValidator implements TimelineValidationRule {
	@Override
	public void validate(TimelineCompileContext context, DiagnosticCollector diagnostics) {
		var track = context.document().getTrack(Timeline.TRACK_ID_CAMERA);
		if (track == null) return;
		for (var clip : track.getClips()) {
			if (clip == null) continue;
			for (var event : clip.getEvents()) {
				if (event != null && !Double.isFinite(event.getTimeSeconds())) {
					diagnostics.add(TimelineDiagnostic.error("non_finite_camera_time",
						"Event in track \"" + track.getName() + "\" has non-finite time: " + event.getTimeSeconds(),
						event.getId(), event.getTimeSeconds()));
				}
			}
		}
	}
}