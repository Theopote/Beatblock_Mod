package com.beatblock.timeline.playback;

import org.jspecify.annotations.Nullable;

/**
 * Semantics of how an event behaves when playback is running or seeking/rewinding.
 */
public enum PlaybackSemantics {
	/**
	 * Stateful effects that change state over time (e.g. slowly building blocks).
	 */
	STATEFUL,

	/**
	 * Transient, one-shot effects (e.g. audio sounds, particle bursts, camera shakes).
	 */
	TRANSIENT,

	/**
	 * Idempotent mutations where repeated executions result in the same state (e.g. set/clear block).
	 */
	IDEMPOTENT;

	public static java.util.Optional<PlaybackSemantics> fromValue(@Nullable Object value) {
		if (value instanceof PlaybackSemantics semantics) {
			return java.util.Optional.of(semantics);
		}
		if (value == null) return java.util.Optional.empty();
		String name = String.valueOf(value).trim();
		if (name.isEmpty()) return java.util.Optional.empty();
		try {
			return java.util.Optional.of(valueOf(name.toUpperCase(java.util.Locale.ROOT)));
		} catch (IllegalArgumentException ignored) {
			return java.util.Optional.empty();
		}
	}
}
