package com.beatblock.timeline.generation;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Resolves which StageObject targets a newly dropped animation preset should bind to.
 * <p>
 * Product model: {@code Preset + Target + Time = StageEvent}. See
 * {@code docs/animation-library-drag-ux.md}.
 * <ul>
 *   <li>explicit preferred ids (world / layer selection) win</li>
 *   <li>else unique targets from selected timeline animation events</li>
 *   <li>else if exactly one registered StageObject exists → bind it</li>
 *   <li>else UNBOUND (empty target; create event and bind later)</li>
 * </ul>
 * Never invents a fake id such as {@code "default"}.
 */
public final class AnimationDropTargetResolver {

	public enum Mode {
		/** Exactly one target — auto-bind. */
		SINGLE,
		/** Multiple targets — UI must confirm PRIMARY vs ALL (see {@link AnimationMultiTargetDropPrompt}). */
		MULTI,
		/** No target — allow unbound StageEvent. */
		UNBOUND
	}

	/**
	 * @param targetObjectIds non-null list; empty when {@link Mode#UNBOUND}
	 */
	public record Result(Mode mode, List<String> targetObjectIds) {
		public Result {
			Objects.requireNonNull(mode, "mode");
			targetObjectIds = List.copyOf(targetObjectIds != null ? targetObjectIds : List.of());
		}

		public boolean isUnbound() {
			return mode == Mode.UNBOUND || targetObjectIds.isEmpty();
		}

		/** Targets to write: one empty string when unbound so callers always loop once. */
		public List<String> targetsForEventCreation() {
			if (isUnbound()) {
				return List.of("");
			}
			return targetObjectIds;
		}
	}

	private AnimationDropTargetResolver() {}

	/**
	 * @param preferredStageObjectIds  world/layer selection (may be null/empty)
	 * @param targetsFromSelectedEvents targets taken from selected animation events
	 * @param registeredStageObjectIds  all known StageObject ids (for the single-object shortcut)
	 */
	public static Result resolve(
		@Nullable Collection<String> preferredStageObjectIds,
		@Nullable Collection<String> targetsFromSelectedEvents,
		@Nullable Collection<String> registeredStageObjectIds
	) {
		List<String> preferred = sanitize(preferredStageObjectIds);
		if (preferred.size() == 1) {
			return new Result(Mode.SINGLE, preferred);
		}
		if (preferred.size() > 1) {
			return new Result(Mode.MULTI, preferred);
		}

		List<String> fromEvents = sanitize(targetsFromSelectedEvents);
		if (fromEvents.size() == 1) {
			return new Result(Mode.SINGLE, fromEvents);
		}
		if (fromEvents.size() > 1) {
			return new Result(Mode.MULTI, fromEvents);
		}

		List<String> registered = sanitize(registeredStageObjectIds);
		if (registered.size() == 1) {
			// Only unambiguous ambient StageObject — never pick first of many.
			return new Result(Mode.SINGLE, registered);
		}

		return new Result(Mode.UNBOUND, List.of());
	}

	/** True when a StageEvent has no bindable target. */
	public static boolean isUnboundTarget(@Nullable String targetObjectId) {
		return targetObjectId == null || targetObjectId.isBlank();
	}

	private static List<String> sanitize(@Nullable Collection<String> raw) {
		if (raw == null || raw.isEmpty()) {
			return List.of();
		}
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String id : raw) {
			if (id == null) continue;
			String trimmed = id.trim();
			if (!trimmed.isEmpty()) {
				unique.add(trimmed);
			}
		}
		return new ArrayList<>(unique);
	}
}
