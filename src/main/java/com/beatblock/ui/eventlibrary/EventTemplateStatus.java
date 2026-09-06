package com.beatblock.ui.eventlibrary;

/** Health of a persisted {@link EventTemplate} against the current animation catalog. */
public enum EventTemplateStatus {
	/** Animation exists; parameters look usable. */
	VALID,
	/** {@code animationTypeId} is not in the current {@code AnimationDefinition} catalog. */
	MISSING_ANIMATION,
	/** Template fields / parameters are inconsistent or unusable. */
	INVALID_PARAMETERS,
	/**
	 * Usable with caveats (e.g. leftover provenance / instance keys from an older save).
	 * Apply is allowed; UI should show a warning.
	 */
	LEGACY
}
