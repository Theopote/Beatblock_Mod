package com.beatblock.timeline.playback;

/**
 * Policy defining compile behavior when validation issues or compilation errors are found.
 */
public enum CompilePolicy {
	/**
	 * Strictly require a clean/valid document. Throws compilation exceptions on errors.
	 */
	STRICT,

	/**
	 * Bypasses degradable issues (e.g. missing targets/presets) by skipping invalid events.
	 */
	SKIP_INVALID_EVENTS
}
