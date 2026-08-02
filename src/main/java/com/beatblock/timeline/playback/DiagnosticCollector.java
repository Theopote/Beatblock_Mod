package com.beatblock.timeline.playback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Mutable diagnostic sink scoped to one validation run. */
public final class DiagnosticCollector {
	private final List<TimelineDiagnostic> diagnostics = new ArrayList<>();
	private @Nullable TimelineSourceLocation currentLocation;

	public void add(TimelineDiagnostic diagnostic) {
		TimelineDiagnostic value = Objects.requireNonNull(diagnostic, "diagnostic");
		diagnostics.add(value.sourceLocation() == null && currentLocation != null
			? value.withSourceLocation(currentLocation) : value);
	}

	public void at(TimelineSourceLocation location, Runnable validation) {
		Objects.requireNonNull(location, "location");
		Objects.requireNonNull(validation, "validation");
		@Nullable TimelineSourceLocation previous = currentLocation;
		currentLocation = location;
		try {
			validation.run();
		} finally {
			currentLocation = previous;
		}
	}
	public List<TimelineDiagnostic> diagnostics() {
		return List.copyOf(diagnostics);
	}
}