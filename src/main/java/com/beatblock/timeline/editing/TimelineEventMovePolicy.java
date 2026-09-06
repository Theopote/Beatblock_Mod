package com.beatblock.timeline.editing;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.TimelineEvent;
import org.jspecify.annotations.Nullable;

/**
 * Product rules for moving timeline events — shared by Properties editors and Timeline drag.
 * <p>
 * Default for ordinary events: clamp into the parent clip range (do not auto-expand the clip).
 * {@link EventType#CAMERA_SEGMENT} heads are not freely movable; their time is pinned.
 */
public final class TimelineEventMovePolicy {

	public record MoveBounds(double minTimeSeconds, double maxTimeSeconds) {
		public MoveBounds {
			if (maxTimeSeconds < minTimeSeconds) {
				double tmp = minTimeSeconds;
				minTimeSeconds = maxTimeSeconds;
				maxTimeSeconds = tmp;
			}
		}

		public double clamp(double timeSeconds) {
			return Math.max(minTimeSeconds, Math.min(maxTimeSeconds, timeSeconds));
		}

		/** True when the event must stay at a single time (e.g. segment head). */
		public boolean isFixed() {
			return Math.abs(maxTimeSeconds - minTimeSeconds) < 1e-12;
		}
	}

	private TimelineEventMovePolicy() {}

	/**
	 * Bounds for dragging or editing {@code event} inside {@code clip}.
	 * Unknown / null inputs fall back to a degenerate [0, 0] range.
	 */
	public static MoveBounds boundsFor(@Nullable Clip clip, @Nullable TimelineEvent event) {
		if (clip == null || event == null) {
			return new MoveBounds(0.0, 0.0);
		}
		if (event.getType() == EventType.CAMERA_SEGMENT) {
			double t = event.getTimeSeconds();
			return new MoveBounds(t, t);
		}
		// CAMERA_KEYFRAME, GLOBAL, ANIMATION, and other container events:
		// keep Properties and Timeline drag on the same clip-range contract.
		return clipRange(clip.getStartTimeSeconds(), clip.getEndTimeSeconds());
	}

	/** Clip-range bounds used by Properties editors when only start/end are known. */
	public static MoveBounds clipRange(double clipStartSeconds, double clipEndSeconds) {
		return new MoveBounds(clipStartSeconds, clipEndSeconds);
	}

	public static double clampToBounds(
		@Nullable Clip clip,
		@Nullable TimelineEvent event,
		double timeSeconds
	) {
		return boundsFor(clip, event).clamp(timeSeconds);
	}
}
