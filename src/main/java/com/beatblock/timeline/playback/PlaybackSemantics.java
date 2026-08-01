package com.beatblock.timeline.playback;

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
	IDEMPOTENT
}
