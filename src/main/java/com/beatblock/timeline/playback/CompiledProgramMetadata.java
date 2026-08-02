package com.beatblock.timeline.playback;

/** Provenance used to identify, cache, and report a compiled performance program. */
public record CompiledProgramMetadata(
	int compilerVersion,
	String projectId,
	long sourceRevision,
	String sourceFingerprint,
	String presetCatalogFingerprint
) {
	public CompiledProgramMetadata {
		if (compilerVersion < 0) throw new IllegalArgumentException("compilerVersion must be non-negative");
		projectId = projectId != null ? projectId : "";
		sourceFingerprint = sourceFingerprint != null ? sourceFingerprint : "";
		presetCatalogFingerprint = presetCatalogFingerprint != null ? presetCatalogFingerprint : "";
	}

	public static CompiledProgramMetadata unknown() {
		return new CompiledProgramMetadata(0, "", -1, "", "");
	}
}
