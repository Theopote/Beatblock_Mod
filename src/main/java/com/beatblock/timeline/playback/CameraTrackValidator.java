package com.beatblock.timeline.playback;

import com.beatblock.automap.camera.CameraShotValidator;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;

final class CameraTrackValidator implements TimelineValidationRule {
	@Override
	public void validate(TimelineCompileContext context, DiagnosticCollector diagnostics) {
		var track = context.document().getTrack(Timeline.TRACK_ID_CAMERA);
		if (track == null) return;
		for (var clip : track.getClips()) {
			if (clip == null) continue;
			double clipStart = clip.getStartTimeSeconds();
			for (var event : clip.getEvents()) {
				if (event == null) continue;
				TimelineSourceLocation location = new TimelineSourceLocation(
					track.getId(), clip.getId(), event.getId(), 0);
				diagnostics.at(location, () -> validateEvent(event, context, clipStart, diagnostics));
			}
		}
	}

	private static void validateEvent(
		com.beatblock.timeline.TimelineEvent event,
		TimelineCompileContext context,
		double clipStartSeconds,
		DiagnosticCollector diagnostics
	) {
		if (!Double.isFinite(event.getTimeSeconds())) {
			diagnostics.add(TimelineDiagnostic.error("non_finite_camera_time",
				"Event in track \"" + Timeline.TRACK_ID_CAMERA + "\" has non-finite time: " + event.getTimeSeconds(),
				event.getId(), event.getTimeSeconds()));
			return;
		}
		if (event.getType() != EventType.CAMERA_SEGMENT) return;
		for (TimelineDiagnostic diagnostic : CameraShotValidator.validateSegment(
			event.getParameters(),
			clipStartSeconds,
			event.getId(),
			context.engine(),
			context.layerManager()
		)) {
			diagnostics.add(diagnostic);
		}
	}
}
