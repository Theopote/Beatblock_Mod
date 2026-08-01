package com.beatblock.timeline.playback;

/**
 * Immutable marker snapshot for formal playback / navigation.
 */
public record CompiledMarker(
	String id,
	double timeSeconds,
	String name,
	String typeName
) {
	public CompiledMarker {
		id = id != null ? id : "";
		name = name != null ? name : "";
		typeName = typeName != null && !typeName.isBlank() ? typeName : "GENERIC";
		if (timeSeconds < 0) {
			timeSeconds = 0;
		}
	}
}
