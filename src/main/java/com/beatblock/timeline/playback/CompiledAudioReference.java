package com.beatblock.timeline.playback;

import org.jspecify.annotations.Nullable;

/**
 * Immutable audio binding snapshot for formal playback (Phase B).
 */
public record CompiledAudioReference(
	String path,
	boolean pathPresent,
	boolean fileExists,
	double durationSeconds,
	@Nullable String assetId
) {
	public static CompiledAudioReference empty() {
		return new CompiledAudioReference("", false, false, 0, null);
	}

	public CompiledAudioReference {
		path = path != null ? path : "";
		if (assetId != null && assetId.isBlank()) {
			assetId = null;
		}
		if (durationSeconds < 0 || Double.isNaN(durationSeconds) || Double.isInfinite(durationSeconds)) {
			durationSeconds = 0;
		}
	}

	public boolean isUsable() {
		return pathPresent && fileExists;
	}
}
