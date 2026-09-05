package com.beatblock.timeline;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import com.beatblock.timeline.binding.AnimationBindingRule;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remaps StageObject ids referenced by Timeline animation events, binding rules,
 * and choreography stage roles (e.g. after BuildLayer merge dissolves source stages).
 */
public final class StageObjectTargetRemapper {

	public record EventTargetPatch(
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull String eventId,
		@NonNull String previousTargetObjectId
	) {}

	/**
	 * Undo payload: previous event targets plus optional snapshots of rules/plan.
	 * {@code previousBindingRulesEncoded == null} means rules were not changed;
	 * empty list means rules were cleared.
	 */
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
	}

	private StageObjectTargetRemapper() {}

	/**
	 * Rewrites every matching {@code targetObject} / {@code targetObjectId} from
	 * {@code fromIds} to {@code toId}. Returns patches for {@link #restore}.
	 */
	public static @NonNull RemapResult remap(
		@Nullable Timeline timeline,
		@Nullable Set<String> fromIds,
		@Nullable String toId
	) {
		if (timeline == null || fromIds == null || fromIds.isEmpty()
			|| toId == null || toId.isBlank()) {
			return new RemapResult(List.of(), null, null, false);
		}

		List<EventTargetPatch> eventPatches = remapAnimationEvents(timeline, fromIds, toId);
		List<Map<String, Object>> previousRules = remapBindingRules(timeline, fromIds, toId);
		PlanRemap planRemap = remapChoreographyPlan(timeline, fromIds, toId);

		return new RemapResult(eventPatches, previousRules, planRemap.previous(), planRemap.changed());
	}

	public static void restore(@Nullable Timeline timeline, @Nullable RemapResult result) {
		if (timeline == null || result == null || result.isEmpty()) {
			return;
		}
		for (EventTargetPatch patch : result.eventPatches()) {
			applyEventTarget(timeline, patch.trackId(), patch.clipId(), patch.eventId(), patch.previousTargetObjectId());
		}
		if (result.previousBindingRulesEncoded() != null) {
			List<AnimationBindingRule> rules = new ArrayList<>();
			for (Map<String, Object> encoded : result.previousBindingRulesEncoded()) {
				AnimationBindingRule rule = AnimationBindingRule.fromMap(encoded);
				if (rule != null) {
					rules.add(rule);
				}
			}
			AnimationBindingEngine.saveRules(timeline, rules);
		}
		if (result.planChanged()) {
			timeline.setMetadata(ChoreographyPlanStore.KEY_PLAN, result.previousPlan());
		}
	}

	private static List<EventTargetPatch> remapAnimationEvents(
		Timeline timeline,
		Set<String> fromIds,
		String toId
	) {
		List<EventTargetPatch> patches = new ArrayList<>();
		boolean anyDirty = false;
		for (Track track : timeline.getTracks()) {
			if (track == null || !Timeline.isAnimationEventsTrackId(track.getId())) {
				continue;
			}
			boolean trackDirty = false;
			for (Clip clip : track.getClips()) {
				if (clip == null) continue;
				for (TimelineEvent event : clip.getEvents()) {
					if (event == null || event.getType() != EventType.ANIMATION) continue;
					Object raw = event.getParameter("targetObject");
					if (!(raw instanceof String previous) || previous.isBlank()) continue;
					if (!fromIds.contains(previous)) continue;
					patches.add(new EventTargetPatch(track.getId(), clip.getId(), event.getId(), previous));
					event.setParameter("targetObject", toId);
					trackDirty = true;
				}
			}
			if (trackDirty) {
				timeline.markAnimationEventsDirty(track.getId());
				anyDirty = true;
			}
		}
		if (anyDirty) {
			timeline.markAnimationEventsDirty();
		}
		return patches;
	}

	private static void applyEventTarget(
		Timeline timeline,
		String trackId,
		String clipId,
		String eventId,
		String targetObjectId
	) {
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent event = clip.getEvent(eventId);
		if (event == null) return;
		event.setParameter("targetObject", targetObjectId);
		timeline.markAnimationEventsDirty(trackId);
	}

	private static @Nullable List<Map<String, Object>> remapBindingRules(
		Timeline timeline,
		Set<String> fromIds,
		String toId
	) {
		List<AnimationBindingRule> rules = AnimationBindingEngine.loadRules(timeline);
		if (rules.isEmpty()) {
			return null;
		}
		boolean changed = false;
		List<Map<String, Object>> previousEncoded = new ArrayList<>(rules.size());
		List<AnimationBindingRule> remapped = new ArrayList<>(rules.size());
		for (AnimationBindingRule rule : rules) {
			previousEncoded.add(rule.toMap());
			String target = rule.targetObjectId();
			if (fromIds.contains(target)) {
				changed = true;
				remapped.add(copyRuleWithTarget(rule, toId));
			} else {
				remapped.add(rule);
			}
		}
		if (!changed) {
			return null;
		}
		AnimationBindingEngine.saveRules(timeline, remapped);
		return previousEncoded;
	}

	private static AnimationBindingRule copyRuleWithTarget(AnimationBindingRule rule, String targetObjectId) {
		return AnimationBindingRule.builder()
			.id(rule.id())
			.name(rule.name())
			.enabled(rule.enabled())
			.sourceFeatureKey(rule.sourceFeatureKey())
			.animationTypeId(rule.animationTypeId())
			.actionMode(rule.actionMode())
			.targetObjectId(targetObjectId)
			.energyThreshold(rule.energyThreshold())
			.energyScale(rule.energyScale())
			.durationSeconds(rule.durationSeconds())
			.cooldownSeconds(rule.cooldownSeconds())
			.probability(rule.probability())
			.spatialMode(rule.spatialMode())
			.sequentialDelaySeconds(rule.sequentialDelaySeconds())
			.sectionFilter(rule.sectionFilter())
			.extraParams(rule.extraParams())
			.build();
	}

	private record PlanRemap(@Nullable ChoreographyPlan previous, boolean changed) {}

	private static PlanRemap remapChoreographyPlan(Timeline timeline, Set<String> fromIds, String toId) {
		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		if (plan == null || plan.stageRoles().isEmpty()) {
			return new PlanRemap(null, false);
		}
		boolean changed = false;
		List<ChoreographyPlan.StageRoleAssignment> roles = new ArrayList<>(plan.stageRoles().size());
		for (ChoreographyPlan.StageRoleAssignment role : plan.stageRoles()) {
			String target = role.targetObjectId();
			if (target != null && fromIds.contains(target)) {
				changed = true;
				roles.add(new ChoreographyPlan.StageRoleAssignment(role.normalizedFeatureKey(), toId));
			} else {
				roles.add(role);
			}
		}
		if (!changed) {
			return new PlanRemap(null, false);
		}
		ChoreographyPlan remapped = new ChoreographyPlan(
			plan.sections(),
			roles,
			plan.motionPhrases(),
			plan.cameraPhrases(),
			plan.vfxPhrases(),
			plan.densityCurve(),
			plan.sectionEdits(),
			plan.musicalStructure(),
			plan.spatialMotifPhrases(),
			plan.choreographyPhrases()
		);
		timeline.setMetadata(ChoreographyPlanStore.KEY_PLAN, remapped);
		return new PlanRemap(plan, true);
	}
}
