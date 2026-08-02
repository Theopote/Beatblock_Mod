package com.beatblock.timeline.playback;

/**
 * Modes of seeking/rewinding in the timeline.
 */
public enum SeekMode {
	/**
	 * Realtime scrubbing and previewing in the editor UI.
	 */
	SCRUB_PREVIEW,

	/**
	 * Reconstruction of states (Stateful and Idempotent events) on rewind.
	 */
	RECONSTRUCT_STATE,

	/**
	 * Direct jump without replaying any past events.
	 */
	JUMP_WITHOUT_REPLAY,

	/** Explicitly replay every event up to the seek target. */
	REPLAY_ALL
}
