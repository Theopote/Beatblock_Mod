package com.beatblock.timeline.playback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Result of {@link TimelineValidator#validate}.
 * <p>
 * Formal play should be blocked when {@link #hasErrors()} is true (Phase A policy).
 */
public final class TimelineValidationReport {

	private final List<TimelineDiagnostic> diagnostics;
	private final int animationEventCount;
	private final int cameraKeyframeCount;
	private final int buildLayerCount;
	private final int markerCount;

	public TimelineValidationReport(
		List<TimelineDiagnostic> diagnostics,
		int animationEventCount,
		int cameraKeyframeCount,
		int buildLayerCount,
		int markerCount
	) {
		this.diagnostics = List.copyOf(diagnostics != null ? diagnostics : List.of());
		this.animationEventCount = Math.max(0, animationEventCount);
		this.cameraKeyframeCount = Math.max(0, cameraKeyframeCount);
		this.buildLayerCount = Math.max(0, buildLayerCount);
		this.markerCount = Math.max(0, markerCount);
	}

	public List<TimelineDiagnostic> diagnostics() {
		return diagnostics;
	}

	public int animationEventCount() {
		return animationEventCount;
	}

	public int cameraKeyframeCount() {
		return cameraKeyframeCount;
	}

	public int buildLayerCount() {
		return buildLayerCount;
	}

	public int markerCount() {
		return markerCount;
	}

	public int errorCount() {
		return count(TimelineDiagnosticSeverity.ERROR);
	}

	public int warningCount() {
		return count(TimelineDiagnosticSeverity.WARNING);
	}

	public int infoCount() {
		return count(TimelineDiagnosticSeverity.INFO);
	}

	public boolean hasErrors() {
		return errorCount() > 0;
	}

	public boolean hasFatalErrors() {
		for (TimelineDiagnostic d : diagnostics) {
			if (d.severity() == TimelineDiagnosticSeverity.ERROR && d.isFatal()) {
				return true;
			}
		}
		return false;
	}

	public boolean hasWarnings() {
		return warningCount() > 0;
	}

	public boolean isClean() {
		return !hasErrors() && !hasWarnings();
	}

	/** Errors and warnings only (excludes pure INFO counters). */
	public List<TimelineDiagnostic> problems() {
		List<TimelineDiagnostic> out = new ArrayList<>();
		for (TimelineDiagnostic d : diagnostics) {
			if (d.severity() == TimelineDiagnosticSeverity.ERROR
				|| d.severity() == TimelineDiagnosticSeverity.WARNING) {
				out.add(d);
			}
		}
		return out;
	}

	private int count(TimelineDiagnosticSeverity severity) {
		Objects.requireNonNull(severity);
		int n = 0;
		for (TimelineDiagnostic d : diagnostics) {
			if (d.severity() == severity) {
				n++;
			}
		}
		return n;
	}
}
