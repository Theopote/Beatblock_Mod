package com.beatblock.timeline.util;

/**
 * Musical duration amount + unit. Timeline storage remains seconds after {@link #toSeconds(double)}.
 */
public record MusicalDuration(double amount, MusicalDurationUnit unit) {

	public MusicalDuration {
		amount = Math.max(0.0, amount);
		unit = unit != null ? unit : MusicalDurationUnit.SECONDS;
	}

	public static MusicalDuration seconds(double seconds) {
		return new MusicalDuration(seconds, MusicalDurationUnit.SECONDS);
	}

	public static MusicalDuration beats(double beats) {
		return new MusicalDuration(beats, MusicalDurationUnit.BEATS);
	}

	public static MusicalDuration bars(double bars) {
		return new MusicalDuration(bars, MusicalDurationUnit.BARS);
	}

	public static MusicalDuration fromSeconds(double seconds, MusicalDurationUnit unit, double bpm) {
		MusicalDurationUnit u = unit != null ? unit : MusicalDurationUnit.SECONDS;
		return new MusicalDuration(u.fromSeconds(seconds, bpm), u);
	}

	public double toSeconds(double bpm) {
		return Math.max(0.05, unit.toSeconds(amount, bpm));
	}

	public String displayLabel() {
		String rounded = amount == Math.rint(amount)
			? String.valueOf((int) Math.rint(amount))
			: String.format(java.util.Locale.ROOT, "%.2f", amount);
		return rounded + " " + switch (unit) {
			case SECONDS -> "s";
			case BEATS -> amount == 1.0 ? "beat" : "beats";
			case BARS -> amount == 1.0 ? "bar" : "bars";
		};
	}
}
