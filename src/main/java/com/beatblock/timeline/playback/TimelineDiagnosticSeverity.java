package com.beatblock.timeline.playback;

/** Severity for pre-play performance checks. */
public enum TimelineDiagnosticSeverity {
	/** Blocks formal play until fixed (or user force-overrides later). */
	ERROR,
	/** Allows play; surface in Performance check. */
	WARNING,
	/** Counts / informational notes. */
	INFO
}
