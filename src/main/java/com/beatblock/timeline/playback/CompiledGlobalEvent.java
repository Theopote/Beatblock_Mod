package com.beatblock.timeline.playback;

/**
 * Immutable global / VFX cue snapshot (Phase C).
 * Covers the timeline global track (lighting, special, stage cues).
 */
public record CompiledGlobalEvent(
	String id,
	double timeSeconds,
	String typeName,
	String name
) {
	public CompiledGlobalEvent {
		id = id != null ? id : "";
		typeName = typeName != null && !typeName.isBlank() ? typeName : "SPECIAL";
		name = name != null ? name : "";
		if (timeSeconds < 0) {
			timeSeconds = 0;
		}
	}
}
