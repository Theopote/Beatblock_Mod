package com.beatblock.timeline.util;

/**
 * Musical duration display/edit units (4/4 assumed, same as {@link MusicTimeFormatter}).
 * Timeline storage remains seconds; UI converts via BPM.
 */
public enum MusicalDurationUnit {
	SECONDS,
	BEATS,
	BARS;

	public static final int BEATS_PER_BAR = 4;
	public static final double FALLBACK_BPM = 120.0;

	public double toSeconds(double amount, double bpm) {
		double safeAmount = Math.max(0.0, amount);
		return switch (this) {
			case SECONDS -> safeAmount;
			case BEATS -> safeAmount * secondsPerBeat(bpm);
			case BARS -> safeAmount * secondsPerBeat(bpm) * BEATS_PER_BAR;
		};
	}

	public double fromSeconds(double seconds, double bpm) {
		double safeSeconds = Math.max(0.0, seconds);
		return switch (this) {
			case SECONDS -> safeSeconds;
			case BEATS -> safeSeconds / secondsPerBeat(bpm);
			case BARS -> safeSeconds / (secondsPerBeat(bpm) * BEATS_PER_BAR);
		};
	}

	public static double effectiveBpm(double bpm) {
		return bpm > 1e-6 ? bpm : FALLBACK_BPM;
	}

	private static double secondsPerBeat(double bpm) {
		return 60.0 / effectiveBpm(bpm);
	}
}
