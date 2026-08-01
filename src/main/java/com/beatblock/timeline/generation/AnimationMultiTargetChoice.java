package com.beatblock.timeline.generation;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * User choice when an animation preset placement resolves to multiple StageObject targets.
 * See {@code docs/animation-library-drag-ux.md}.
 */
public enum AnimationMultiTargetChoice {
	/** Bind only the primary (first) selected target. */
	PRIMARY,
	/** One event per selected target. */
	ALL
	// GROUP event deferred — not offered until Group Event model exists
	;

	/**
	 * Expand multi-target candidates into concrete target ids for event creation.
	 */
	public static List<String> expand(
		@Nullable List<String> candidates,
		@Nullable AnimationMultiTargetChoice choice
	) {
		if (candidates == null || candidates.isEmpty()) {
			return List.of("");
		}
		AnimationMultiTargetChoice resolved = choice != null ? choice : ALL;
		return switch (resolved) {
			case PRIMARY -> List.of(candidates.getFirst());
			case ALL -> List.copyOf(candidates);
		};
	}
}
