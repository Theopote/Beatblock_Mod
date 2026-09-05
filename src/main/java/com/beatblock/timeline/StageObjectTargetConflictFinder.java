package com.beatblock.timeline;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Diagnoses overlapping animation events that share the same StageObject target
 * (e.g. after BuildLayer merge remaps multiple sources onto one stage id).
 * <p>
 * Diagnosis only — does not rewrite Timeline content.
 */
public final class StageObjectTargetConflictFinder {

	private static final double TIME_EPSILON = 1e-6;

	public record Overlap(
		@NonNull String targetObjectId,
		@NonNull String eventIdA,
		@NonNull String eventIdB,
		double overlapStartSeconds,
		double overlapEndSeconds
	) {}

	public record ConflictSummary(@NonNull List<Overlap> overlaps) {
		public ConflictSummary {
			overlaps = overlaps != null ? List.copyOf(overlaps) : List.of();
		}

		public boolean isEmpty() {
			return overlaps.isEmpty();
		}

		public int count() {
			return overlaps.size();
		}
	}

	private StageObjectTargetConflictFinder() {}

	/**
	 * Finds pairwise overlaps among stage events whose target equals {@code targetObjectId}.
	 * When {@code targetObjectId} is blank, scans all non-blank targets.
	 */
	public static @NonNull ConflictSummary findOverlaps(
		@Nullable Timeline timeline,
		@Nullable String targetObjectId
	) {
		if (timeline == null) {
			return new ConflictSummary(List.of());
		}
		List<TimelineAnimationEvent> candidates = new ArrayList<>();
		for (TimelineAnimationEvent event : timeline.getStageEvents()) {
			if (event == null || event.isUnboundTarget()) continue;
			if (targetObjectId != null && !targetObjectId.isBlank()
				&& !targetObjectId.equals(event.getTargetObjectId())) {
				continue;
			}
			candidates.add(event);
		}
		candidates.sort(Comparator
			.comparing(TimelineAnimationEvent::getTargetObjectId)
			.thenComparingDouble(TimelineAnimationEvent::getTimeSeconds)
			.thenComparing(TimelineAnimationEvent::getEventId));

		List<Overlap> overlaps = new ArrayList<>();
		for (int i = 0; i < candidates.size(); i++) {
			TimelineAnimationEvent a = candidates.get(i);
			for (int j = i + 1; j < candidates.size(); j++) {
				TimelineAnimationEvent b = candidates.get(j);
				if (!a.getTargetObjectId().equals(b.getTargetObjectId())) {
					break;
				}
				double start = Math.max(a.getTimeSeconds(), b.getTimeSeconds());
				double end = Math.min(a.getEndTimeSeconds(), b.getEndTimeSeconds());
				if (end - start > TIME_EPSILON) {
					overlaps.add(new Overlap(
						a.getTargetObjectId(),
						a.getEventId(),
						b.getEventId(),
						start,
						end
					));
				}
			}
		}
		return new ConflictSummary(overlaps);
	}
}
