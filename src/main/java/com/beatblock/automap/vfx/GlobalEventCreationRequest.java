package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.GlobalEventPayload;

import java.util.Objects;

/** One-way creation intent for a global / VFX cue at a timeline time. */
public record GlobalEventCreationRequest(
	double timeSeconds,
	GlobalEventPayload payload
) {
	public GlobalEventCreationRequest {
		timeSeconds = Math.max(0.0, timeSeconds);
		Objects.requireNonNull(payload, "payload");
	}
}
