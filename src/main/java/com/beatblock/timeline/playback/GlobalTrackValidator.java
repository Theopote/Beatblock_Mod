package com.beatblock.timeline.playback;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;

final class GlobalTrackValidator implements TimelineValidationRule {
	@Override
	public void validate(TimelineCompileContext context, DiagnosticCollector diagnostics) {
		for (var track : context.document().getTracks()) {
			if (track == null) continue;
			for (var clip : track.getClips()) {
				if (clip == null) continue;
				for (var event : clip.getEvents()) {
					if (event == null || (event.getType() != EventType.GLOBAL
						&& !Timeline.TRACK_ID_GLOBAL.equals(track.getId()))) continue;
					double time = event.getTimeSeconds();
					if (!Double.isFinite(time)) {
						diagnostics.add(TimelineDiagnostic.error("non_finite_global_time",
							"Event in track \"" + track.getName() + "\" has non-finite time: " + time,
							event.getId(), time));
					} else if (time < 0) {
						diagnostics.add(TimelineDiagnostic.error("negative_global_time",
							"Global event in track \"" + track.getName() + "\" has negative time: " + time,
							event.getId(), time));
					}
					try {
						GlobalEventPayload payload = GlobalEventPayloadCodec.decode(event.getParameters());
						if (payload instanceof GlobalEventPayload.Generic generic) {
							diagnostics.add(TimelineDiagnostic.warning(TimelineValidator.RULE_UNKNOWN_GLOBAL_EVENT,
								"Unknown global event type \"" + generic.typeName() + "\" will not be executed",
								event.getId(), time));
						}
					} catch (RuntimeException error) {
						diagnostics.add(TimelineDiagnostic.error(TimelineValidator.RULE_INVALID_GLOBAL_PAYLOAD,
							"Invalid global payload for event " + event.getId() + ": " + error.getMessage(),
							event.getId(), time));
					}
				}
			}
		}
	}
}