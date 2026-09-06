package com.beatblock.timeline.playback;

/**
 * Immutable marker snapshot for formal playback / navigation.
 * <p>
 * {@code typeName} is metadata for UI / fingerprint only.
 * Annotation types (GENERIC / DROP / CAMERA / FX) must never drive runtime execution;
 * only SECTION may participate in section lookup / binding (see {@link com.beatblock.timeline.MarkerType}).
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
