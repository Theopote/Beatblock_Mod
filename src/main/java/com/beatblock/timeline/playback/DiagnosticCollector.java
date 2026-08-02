package com.beatblock.timeline.playback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mutable diagnostic sink scoped to one validation run. */
public final class DiagnosticCollector {
	private final List<TimelineDiagnostic> diagnostics = new ArrayList<>();

	public void add(TimelineDiagnostic diagnostic) {
		diagnostics.add(Objects.requireNonNull(diagnostic, "diagnostic"));
	}

	public List<TimelineDiagnostic> diagnostics() {
		return List.copyOf(diagnostics);
	}
}