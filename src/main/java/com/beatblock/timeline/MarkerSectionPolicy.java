package com.beatblock.timeline;

import org.jspecify.annotations.Nullable;

/**
 * SECTION boundary uniqueness: at most one SECTION marker per timestamp.
 * <p>
 * Policy: later write wins unless the occupant is {@link MarkerEditState#LOCKED}.
 */
public final class MarkerSectionPolicy {

	public static final double SAME_TIME_EPSILON = 1e-4;

	public enum CollisionAction {
		NONE,
		REPLACE_EXISTING,
		REJECT
	}

	public record Collision(
		CollisionAction action,
		@Nullable TimelineMarker existing
	) {
		public static final Collision NONE = new Collision(CollisionAction.NONE, null);
	}

	private MarkerSectionPolicy() {
	}

	public static boolean sameTime(double a, double b) {
		return Math.abs(a - b) <= SAME_TIME_EPSILON;
	}

	public static @Nullable TimelineMarker findSectionAtTime(
		@Nullable Timeline timeline,
		double timeSeconds,
		@Nullable String excludeMarkerId
	) {
		if (timeline == null) {
			return null;
		}
		for (TimelineMarker marker : timeline.getMarkers()) {
			if (marker == null || marker.getType() != MarkerType.SECTION) {
				continue;
			}
			if (excludeMarkerId != null && excludeMarkerId.equals(marker.getId())) {
				continue;
			}
			if (sameTime(marker.getTimeSeconds(), timeSeconds)) {
				return marker;
			}
		}
		return null;
	}

	public static Collision resolveForWrite(
		@Nullable Timeline timeline,
		@Nullable TimelineMarker candidate
	) {
		if (timeline == null || candidate == null || candidate.getType() != MarkerType.SECTION) {
			return Collision.NONE;
		}
		TimelineMarker existing = findSectionAtTime(timeline, candidate.getTimeSeconds(), candidate.getId());
		if (existing == null) {
			return Collision.NONE;
		}
		if (MarkerEditPolicy.isLocked(existing)) {
			return new Collision(CollisionAction.REJECT, existing);
		}
		return new Collision(CollisionAction.REPLACE_EXISTING, existing);
	}
}
