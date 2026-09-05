package com.beatblock.timeline;

import com.beatblock.automap.choreography.ChoreographyPlan;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @deprecated Prefer {@link StageObjectReferenceService}. Kept as a thin facade for merge callers/tests.
 */
@Deprecated
public final class StageObjectTargetRemapper {

	public record EventTargetPatch(
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull String eventId,
		@NonNull String previousTargetObjectId
	) {}

	public record RemapResult(
		@NonNull List<EventTargetPatch> eventPatches,
		@Nullable List<Map<String, Object>> previousBindingRulesEncoded,
		@Nullable ChoreographyPlan previousPlan,
		boolean planChanged
	) {
		public RemapResult {
			eventPatches = eventPatches != null ? List.copyOf(eventPatches) : List.of();
			if (previousBindingRulesEncoded != null) {
				previousBindingRulesEncoded = List.copyOf(previousBindingRulesEncoded);
			}
		}

		public boolean isEmpty() {
			return eventPatches.isEmpty() && previousBindingRulesEncoded == null && !planChanged;
		}

		static RemapResult from(StageObjectReferenceService.MutationResult mutation) {
			if (mutation == null || mutation.isEmpty()) {
				return new RemapResult(List.of(), null, null, false);
			}
			List<EventTargetPatch> patches = new ArrayList<>(mutation.eventPatches().size());
			for (StageObjectReferenceService.EventTargetPatch p : mutation.eventPatches()) {
				patches.add(new EventTargetPatch(p.trackId(), p.clipId(), p.eventId(), p.previousTargetObjectId()));
			}
			return new RemapResult(
				patches,
				mutation.previousBindingRulesEncoded(),
				mutation.previousPlan(),
				mutation.planChanged()
			);
		}
	}

	private StageObjectTargetRemapper() {}

	public static @NonNull RemapResult remap(
		@Nullable Timeline timeline,
		@Nullable Set<String> fromIds,
		@Nullable String toId
	) {
		return RemapResult.from(StageObjectReferenceService.remap(timeline, fromIds, toId));
	}

	public static void restore(@Nullable Timeline timeline, @Nullable RemapResult result) {
		if (timeline == null || result == null || result.isEmpty()) {
			return;
		}
		// Full restore goes through the service mutation shape.
		List<StageObjectReferenceService.EventTargetPatch> eventPatches = new ArrayList<>();
		for (EventTargetPatch p : result.eventPatches()) {
			eventPatches.add(new StageObjectReferenceService.EventTargetPatch(
				p.trackId(), p.clipId(), p.eventId(), p.previousTargetObjectId()));
		}
		StageObjectReferenceService.restore(timeline, new StageObjectReferenceService.MutationResult(
			eventPatches,
			List.of(),
			result.previousBindingRulesEncoded(),
			result.previousPlan(),
			result.planChanged(),
			null,
			false,
			null,
			false
		));
	}
}
