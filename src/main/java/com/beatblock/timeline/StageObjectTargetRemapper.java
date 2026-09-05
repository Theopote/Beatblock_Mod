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

	public static final class RemapResult {
		private final @NonNull List<EventTargetPatch> eventPatches;
		private final @Nullable List<Map<String, Object>> previousBindingRulesEncoded;
		private final @Nullable ChoreographyPlan previousPlan;
		private final boolean planChanged;

		public RemapResult(
			@Nullable List<EventTargetPatch> eventPatches,
			@Nullable List<Map<String, Object>> previousBindingRulesEncoded,
			@Nullable ChoreographyPlan previousPlan,
			boolean planChanged
		) {
			this.eventPatches = eventPatches != null ? List.copyOf(eventPatches) : List.of();
			if (previousBindingRulesEncoded != null) {
				List<Map<String, Object>> copied = new ArrayList<>(previousBindingRulesEncoded.size());
				for (Map<String, Object> map : previousBindingRulesEncoded) {
					copied.add(map != null ? Map.copyOf(map) : Map.of());
				}
				this.previousBindingRulesEncoded = List.copyOf(copied);
			} else {
				this.previousBindingRulesEncoded = null;
			}
			this.previousPlan = previousPlan;
			this.planChanged = planChanged;
		}

		public @NonNull List<EventTargetPatch> eventPatches() {
			return eventPatches;
		}

		public @Nullable List<Map<String, Object>> previousBindingRulesEncoded() {
			return previousBindingRulesEncoded == null ? null : List.copyOf(previousBindingRulesEncoded);
		}

		public @Nullable ChoreographyPlan previousPlan() {
			return previousPlan;
		}

		public boolean planChanged() {
			return planChanged;
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
