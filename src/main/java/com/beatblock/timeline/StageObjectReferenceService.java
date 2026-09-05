package com.beatblock.timeline;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapRule;
import com.beatblock.automap.camera.CameraSegmentSemantics;
import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.automap.camera.CameraSubjectKind;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.ChoreographyVfx;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.TargetSet;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapSettingsStore;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import com.beatblock.timeline.binding.AnimationBindingRule;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified StageObject reference graph over Timeline + session AutoMap settings.
 * <p>
 * Covers find / remap (merge) / clear (delete) / restore for all known target-bearing sites.
 * <p>
 * <b>Maintenance rule:</b> any new persisted field that stores {@code targetObjectId},
 * {@code subjectObjectId}, or a collection of StageObject target ids must be registered
 * here (collector + remap/clear/restore). Do not add ad-hoc string scans elsewhere.
 * A future {@code StageObjectReferenceContributor} plugin split is optional; keep sites
 * in this service until that exists.
 */
public final class StageObjectReferenceService {

	public enum ReferenceType {
		ANIMATION_EVENT,
		BINDING_RULE,
		STAGE_ROLE,
		GRAMMAR_TARGET,
		AUTOMAP_RULE,
		AUTOMAP_FEATURE_TARGET,
		CAMERA_PHRASE,
		VFX_TARGET,
		CAMERA_SEGMENT,
		AUTOMAP_SETTINGS
	}

	public record StageObjectReference(
		@NonNull ReferenceType type,
		@NonNull String ownerId,
		@NonNull String targetObjectId
	) {}

	public record ReferenceSummary(@NonNull List<StageObjectReference> references) {
		public ReferenceSummary {
			references = references != null ? List.copyOf(references) : List.of();
		}

		public boolean isEmpty() {
			return references.isEmpty();
		}

		public int count() {
			return references.size();
		}

		public int countOf(ReferenceType type) {
			int n = 0;
			for (StageObjectReference ref : references) {
				if (ref.type() == type) n++;
			}
			return n;
		}

		/**
		 * Counts by reference type for UI localization (callers resolve labels via BBTexts).
		 */
		public Map<ReferenceType, Integer> countsByType() {
			Map<ReferenceType, Integer> out = new EnumMap<>(ReferenceType.class);
			for (StageObjectReference ref : references) {
				out.merge(ref.type(), 1, Integer::sum);
			}
			return out;
		}
	}

	public record EventTargetPatch(
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull String eventId,
		@NonNull String previousTargetObjectId
	) {}

	public static final class CameraSegmentPatch {
		private final @NonNull String clipId;
		private final @NonNull String eventId;
		private final @NonNull Map<String, Object> previousParameters;

		public CameraSegmentPatch(
			@NonNull String clipId,
			@NonNull String eventId,
			@Nullable Map<String, Object> previousParameters
		) {
			this.clipId = clipId != null ? clipId : "";
			this.eventId = eventId != null ? eventId : "";
			this.previousParameters = previousParameters != null ? Map.copyOf(previousParameters) : Map.of();
		}

		public @NonNull String clipId() { return clipId; }
		public @NonNull String eventId() { return eventId; }
		public @NonNull Map<String, Object> previousParameters() { return Map.copyOf(previousParameters); }
	}

	/**
	 * Undo payload for {@link #remap} / {@link #clear}.
	 * Nullable snapshot fields mean that store was not mutated.
	 */
	public static final class MutationResult {
		private final @NonNull List<EventTargetPatch> eventPatches;
		private final @NonNull List<CameraSegmentPatch> cameraPatches;
		private final @Nullable List<Map<String, Object>> previousBindingRulesEncoded;
		private final @Nullable ChoreographyPlan previousPlan;
		private final boolean planChanged;
		private final @Nullable AutoMapConfig previousConfig;
		private final boolean configChanged;
		private final @Nullable List<String> previousAutoMapSettingsTargets;
		private final boolean autoMapSettingsChanged;

		public MutationResult(
			@Nullable List<EventTargetPatch> eventPatches,
			@Nullable List<CameraSegmentPatch> cameraPatches,
			@Nullable List<Map<String, Object>> previousBindingRulesEncoded,
			@Nullable ChoreographyPlan previousPlan,
			boolean planChanged,
			@Nullable AutoMapConfig previousConfig,
			boolean configChanged,
			@Nullable List<String> previousAutoMapSettingsTargets,
			boolean autoMapSettingsChanged
		) {
			this.eventPatches = eventPatches != null ? List.copyOf(eventPatches) : List.of();
			this.cameraPatches = cameraPatches != null ? List.copyOf(cameraPatches) : List.of();
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
			this.previousConfig = previousConfig;
			this.configChanged = configChanged;
			this.previousAutoMapSettingsTargets = previousAutoMapSettingsTargets != null
				? List.copyOf(previousAutoMapSettingsTargets) : null;
			this.autoMapSettingsChanged = autoMapSettingsChanged;
		}

		public @NonNull List<EventTargetPatch> eventPatches() { return eventPatches; }
		public @NonNull List<CameraSegmentPatch> cameraPatches() { return cameraPatches; }
		public @Nullable List<Map<String, Object>> previousBindingRulesEncoded() {
			return previousBindingRulesEncoded == null ? null : List.copyOf(previousBindingRulesEncoded);
		}
		public @Nullable ChoreographyPlan previousPlan() { return previousPlan; }
		public boolean planChanged() { return planChanged; }
		public @Nullable AutoMapConfig previousConfig() { return previousConfig; }
		public boolean configChanged() { return configChanged; }
		public @Nullable List<String> previousAutoMapSettingsTargets() {
			return previousAutoMapSettingsTargets == null ? null : List.copyOf(previousAutoMapSettingsTargets);
		}
		public boolean autoMapSettingsChanged() { return autoMapSettingsChanged; }

		public boolean isEmpty() {
			return eventPatches.isEmpty()
				&& cameraPatches.isEmpty()
				&& previousBindingRulesEncoded == null
				&& !planChanged
				&& !configChanged
				&& !autoMapSettingsChanged;
		}
	}

	private StageObjectReferenceService() {}

	public static @NonNull ReferenceSummary find(
		@Nullable Timeline timeline,
		@Nullable Set<String> stageObjectIds
	) {
		if (stageObjectIds == null || stageObjectIds.isEmpty()) {
			return new ReferenceSummary(List.of());
		}
		Set<String> ids = Set.copyOf(stageObjectIds);
		List<StageObjectReference> refs = new ArrayList<>();
		collectAnimationEventRefs(timeline, ids, refs);
		collectBindingRuleRefs(timeline, ids, refs);
		collectPlanRefs(timeline, ids, refs);
		collectConfigRefs(timeline, ids, refs);
		collectCameraSegmentRefs(timeline, ids, refs);
		collectAutoMapSettingsRefs(ids, refs);
		return new ReferenceSummary(refs);
	}

	public static boolean hasReferences(@Nullable Timeline timeline, @Nullable Set<String> stageObjectIds) {
		return !find(timeline, stageObjectIds).isEmpty();
	}

	/**
	 * Rewrite every matching StageObject id to {@code toId} (merge path).
	 */
	public static @NonNull MutationResult remap(
		@Nullable Timeline timeline,
		@Nullable Set<String> fromIds,
		@Nullable String toId
	) {
		if (fromIds == null || fromIds.isEmpty() || toId == null || toId.isBlank()) {
			return emptyMutation();
		}
		return mutate(timeline, Set.copyOf(fromIds), toId);
	}

	/**
	 * Clear / unbind every matching StageObject id (delete path).
	 * Animation targets become unbound ({@code ""}); list memberships drop the id;
	 * camera/VFX subjects fall back to {@link CameraSubject#allStageObjects()}.
	 */
	public static @NonNull MutationResult clear(
		@Nullable Timeline timeline,
		@Nullable Set<String> stageObjectIds
	) {
		if (stageObjectIds == null || stageObjectIds.isEmpty()) {
			return emptyMutation();
		}
		return mutate(timeline, Set.copyOf(stageObjectIds), null);
	}

	public static void restore(@Nullable Timeline timeline, @Nullable MutationResult result) {
		if (result == null || result.isEmpty()) {
			return;
		}
		for (EventTargetPatch patch : result.eventPatches()) {
			if (timeline == null) break;
			applyEventTarget(timeline, patch.trackId(), patch.clipId(), patch.eventId(), patch.previousTargetObjectId());
		}
		for (CameraSegmentPatch patch : result.cameraPatches()) {
			if (timeline == null) break;
			applyCameraSegmentParams(timeline, patch.clipId(), patch.eventId(), patch.previousParameters());
		}
		List<Map<String, Object>> previousRules = result.previousBindingRulesEncoded();
		if (timeline != null && previousRules != null) {
			List<AnimationBindingRule> rules = new ArrayList<>();
			for (Map<String, Object> encoded : previousRules) {
				AnimationBindingRule rule = AnimationBindingRule.fromMap(encoded);
				if (rule != null) rules.add(rule);
			}
			AnimationBindingEngine.saveRules(timeline, rules);
		}
		if (timeline != null && result.planChanged()) {
			timeline.setMetadata(ChoreographyPlanStore.KEY_PLAN, result.previousPlan());
		}
		if (timeline != null && result.configChanged()) {
			timeline.setMetadata(ChoreographyPlanStore.KEY_CONFIG, result.previousConfig());
		}
		List<String> previousSettingsTargets = result.previousAutoMapSettingsTargets();
		if (result.autoMapSettingsChanged() && previousSettingsTargets != null) {
			AutoMapSettingsStore.current().setTargetObjectIds(previousSettingsTargets);
		}
	}

	private static MutationResult emptyMutation() {
		return new MutationResult(List.of(), List.of(), null, null, false, null, false, null, false);
	}

	private static MutationResult mutate(@Nullable Timeline timeline, Set<String> fromIds, @Nullable String toId) {
		List<EventTargetPatch> eventPatches = mutateAnimationEvents(timeline, fromIds, toId);
		List<CameraSegmentPatch> cameraPatches = mutateCameraSegments(timeline, fromIds, toId);
		List<Map<String, Object>> previousRules = mutateBindingRules(timeline, fromIds, toId);
		PlanMutation planMutation = mutatePlan(timeline, fromIds, toId);
		ConfigMutation configMutation = mutateConfig(timeline, fromIds, toId);
		SettingsMutation settingsMutation = mutateAutoMapSettings(fromIds, toId);
		return new MutationResult(
			eventPatches,
			cameraPatches,
			previousRules,
			planMutation.previous(),
			planMutation.changed(),
			configMutation.previous(),
			configMutation.changed(),
			settingsMutation.previous(),
			settingsMutation.changed()
		);
	}

	// --- find collectors ---

	private static void collectAnimationEventRefs(
		@Nullable Timeline timeline,
		Set<String> ids,
		List<StageObjectReference> out
	) {
		if (timeline == null) return;
		for (Track track : timeline.getTracks()) {
			if (track == null || !Timeline.isAnimationEventsTrackId(track.getId())) continue;
			for (Clip clip : track.getClips()) {
				if (clip == null) continue;
				for (TimelineEvent event : clip.getEvents()) {
					if (event == null || event.getType() != EventType.ANIMATION) continue;
					String target = stringParam(event.getParameter("targetObject"));
					if (ids.contains(target)) {
						out.add(new StageObjectReference(ReferenceType.ANIMATION_EVENT, event.getId(), target));
					}
				}
			}
		}
	}

	private static void collectBindingRuleRefs(
		@Nullable Timeline timeline,
		Set<String> ids,
		List<StageObjectReference> out
	) {
		if (timeline == null) return;
		for (AnimationBindingRule rule : AnimationBindingEngine.loadRules(timeline)) {
			String target = rule.targetObjectId();
			if (ids.contains(target)) {
				out.add(new StageObjectReference(ReferenceType.BINDING_RULE, rule.id(), target));
			}
		}
	}

	private static void collectPlanRefs(
		@Nullable Timeline timeline,
		Set<String> ids,
		List<StageObjectReference> out
	) {
		ChoreographyPlan plan = timeline != null ? ChoreographyPlanStore.loadPlan(timeline) : null;
		if (plan == null) return;
		int roleIndex = 0;
		for (ChoreographyPlan.StageRoleAssignment role : plan.stageRoles()) {
			String target = role.targetObjectId();
			if (target != null && ids.contains(target)) {
				out.add(new StageObjectReference(
					ReferenceType.STAGE_ROLE,
					role.normalizedFeatureKey() + "#" + roleIndex,
					target
				));
			}
			roleIndex++;
		}
		int phraseIndex = 0;
		for (ChoreographyPhrase phrase : plan.choreographyPhrases()) {
			if (phrase == null || phrase.targets() == null) {
				phraseIndex++;
				continue;
			}
			for (String target : phrase.targets().objectIds()) {
				if (ids.contains(target)) {
					out.add(new StageObjectReference(
						ReferenceType.GRAMMAR_TARGET,
						"phrase#" + phraseIndex,
						target
					));
				}
			}
			phraseIndex++;
		}
		int camIndex = 0;
		for (ChoreographyPlan.CameraPhrase camera : plan.cameraPhrases()) {
			if (isStageObjectSubject(camera.subjectKind()) && ids.contains(camera.subjectRef())) {
				out.add(new StageObjectReference(
					ReferenceType.CAMERA_PHRASE,
					"camera#" + camIndex,
					camera.subjectRef()
				));
			}
			camIndex++;
		}
		int vfxIndex = 0;
		for (ChoreographyVfx vfx : plan.vfxPhrases()) {
			if (vfx instanceof ChoreographyVfx.ParticleBurst burst) {
				CameraSubject subject = burst.target();
				if (subject != null && isStageObjectKind(subject.kind()) && ids.contains(subject.refId())) {
					out.add(new StageObjectReference(
						ReferenceType.VFX_TARGET,
						"vfx#" + vfxIndex,
						subject.refId()
					));
				}
			}
			vfxIndex++;
		}
	}

	private static void collectConfigRefs(
		@Nullable Timeline timeline,
		Set<String> ids,
		List<StageObjectReference> out
	) {
		AutoMapConfig config = timeline != null ? ChoreographyPlanStore.loadConfig(timeline) : null;
		if (config == null) return;
		int ruleIndex = 0;
		for (AutoMapRule rule : config.getRules()) {
			String target = rule.getTargetObjectId();
			if (target != null && ids.contains(target)) {
				out.add(new StageObjectReference(
					ReferenceType.AUTOMAP_RULE,
					rule.getFeatureKey() + "#" + ruleIndex,
					target
				));
			}
			ruleIndex++;
		}
		for (Map.Entry<String, String> e : config.getTargetByNormalizedFeature().entrySet()) {
			if (ids.contains(e.getValue())) {
				out.add(new StageObjectReference(
					ReferenceType.AUTOMAP_FEATURE_TARGET,
					e.getKey(),
					e.getValue()
				));
			}
		}
	}

	private static void collectCameraSegmentRefs(
		@Nullable Timeline timeline,
		Set<String> ids,
		List<StageObjectReference> out
	) {
		if (timeline == null) return;
		Track track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (track == null) return;
		for (Clip clip : track.getClips()) {
			if (clip == null) continue;
			for (TimelineEvent event : clip.getEvents()) {
				if (event == null || event.getType() != EventType.CAMERA_SEGMENT) continue;
				CameraSubject follow = CameraSegmentSemantics.followSubjectFrom(event.getParameters());
				if (follow != null && isStageObjectKind(follow.kind()) && ids.contains(follow.refId())) {
					out.add(new StageObjectReference(
						ReferenceType.CAMERA_SEGMENT,
						event.getId(),
						follow.refId()
					));
				}
			}
		}
	}

	private static void collectAutoMapSettingsRefs(Set<String> ids, List<StageObjectReference> out) {
		AutoMapSettings settings = AutoMapSettingsStore.current();
		for (String target : settings.getTargetObjectIds()) {
			if (ids.contains(target)) {
				out.add(new StageObjectReference(ReferenceType.AUTOMAP_SETTINGS, "session", target));
			}
		}
	}

	// --- mutators ---

	private static List<EventTargetPatch> mutateAnimationEvents(
		@Nullable Timeline timeline,
		Set<String> fromIds,
		@Nullable String toId
	) {
		if (timeline == null) return List.of();
		List<EventTargetPatch> patches = new ArrayList<>();
		for (Track track : timeline.getTracks()) {
			if (track == null || !Timeline.isAnimationEventsTrackId(track.getId())) continue;
			boolean trackDirty = false;
			for (Clip clip : track.getClips()) {
				if (clip == null) continue;
				for (TimelineEvent event : clip.getEvents()) {
					if (event == null || event.getType() != EventType.ANIMATION) continue;
					String previous = stringParam(event.getParameter("targetObject"));
					if (!fromIds.contains(previous)) continue;
					patches.add(new EventTargetPatch(track.getId(), clip.getId(), event.getId(), previous));
					event.setParameter("targetObject", toId != null ? toId : "");
					trackDirty = true;
				}
			}
			if (trackDirty) {
				timeline.markAnimationEventsDirty(track.getId());
			}
		}
		if (!patches.isEmpty()) {
			timeline.markAnimationEventsDirty();
		}
		return patches;
	}

	private static List<CameraSegmentPatch> mutateCameraSegments(
		@Nullable Timeline timeline,
		Set<String> fromIds,
		@Nullable String toId
	) {
		if (timeline == null) return List.of();
		Track track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (track == null) return List.of();
		List<CameraSegmentPatch> patches = new ArrayList<>();
		for (Clip clip : track.getClips()) {
			if (clip == null) continue;
			for (TimelineEvent event : clip.getEvents()) {
				if (event == null || event.getType() != EventType.CAMERA_SEGMENT) continue;
				CameraSubject follow = CameraSegmentSemantics.followSubjectFrom(event.getParameters());
				if (follow == null || !isStageObjectKind(follow.kind()) || !fromIds.contains(follow.refId())) {
					continue;
				}
				Map<String, Object> previous = new LinkedHashMap<>(event.getParameters());
				patches.add(new CameraSegmentPatch(clip.getId(), event.getId(), previous));
				CameraSubject rewritten = rewriteSubject(follow, fromIds, toId);
				Map<String, Object> next = new LinkedHashMap<>(previous);
				next.put(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_KIND, rewritten.kind().name());
				if (rewritten.refId().isBlank()) {
					next.remove(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_REF);
				} else {
					next.put(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_REF, rewritten.refId());
				}
				event.setParameters(next);
			}
		}
		return patches;
	}

	private static @Nullable List<Map<String, Object>> mutateBindingRules(
		@Nullable Timeline timeline,
		Set<String> fromIds,
		@Nullable String toId
	) {
		if (timeline == null) return null;
		List<AnimationBindingRule> rules = AnimationBindingEngine.loadRules(timeline);
		if (rules.isEmpty()) return null;
		boolean changed = false;
		List<Map<String, Object>> previousEncoded = new ArrayList<>(rules.size());
		List<AnimationBindingRule> next = new ArrayList<>(rules.size());
		for (AnimationBindingRule rule : rules) {
			previousEncoded.add(rule.toMap());
			String target = rule.targetObjectId();
			if (fromIds.contains(target)) {
				changed = true;
				String rewritten = toId != null ? toId : "";
				if (rewritten.isBlank()) {
					// Drop invalid unbound rules from the active set.
					continue;
				}
				next.add(copyRuleWithTarget(rule, rewritten));
			} else {
				next.add(rule);
			}
		}
		if (!changed) return null;
		AnimationBindingEngine.saveRules(timeline, next);
		return previousEncoded;
	}

	private record PlanMutation(@Nullable ChoreographyPlan previous, boolean changed) {}

	private static PlanMutation mutatePlan(
		@Nullable Timeline timeline,
		Set<String> fromIds,
		@Nullable String toId
	) {
		if (timeline == null) return new PlanMutation(null, false);
		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		if (plan == null) return new PlanMutation(null, false);

		boolean changed = false;
		List<ChoreographyPlan.StageRoleAssignment> roles = new ArrayList<>(plan.stageRoles().size());
		for (ChoreographyPlan.StageRoleAssignment role : plan.stageRoles()) {
			String target = role.targetObjectId();
			if (target != null && fromIds.contains(target)) {
				changed = true;
				if (toId != null && !toId.isBlank()) {
					roles.add(new ChoreographyPlan.StageRoleAssignment(role.normalizedFeatureKey(), toId));
				}
				// clear: drop role
			} else {
				roles.add(role);
			}
		}

		List<ChoreographyPhrase> phrases = new ArrayList<>(plan.choreographyPhrases().size());
		for (ChoreographyPhrase phrase : plan.choreographyPhrases()) {
			TargetSet rewritten = rewriteTargetSet(phrase.targets(), fromIds, toId);
			if (!rewritten.equals(phrase.targets())) {
				changed = true;
				phrases.add(new ChoreographyPhrase(
					phrase.trigger(),
					rewritten,
					phrase.spatial(),
					phrase.motion(),
					phrase.timing(),
					phrase.intensity(),
					phrase.variation(),
					phrase.sectionIndex(),
					phrase.timingSnap(),
					phrase.layer()
				));
			} else {
				phrases.add(phrase);
			}
		}

		List<ChoreographyPlan.CameraPhrase> cameras = new ArrayList<>(plan.cameraPhrases().size());
		for (ChoreographyPlan.CameraPhrase camera : plan.cameraPhrases()) {
			if (isStageObjectSubject(camera.subjectKind()) && fromIds.contains(camera.subjectRef())) {
				changed = true;
				if (toId != null && !toId.isBlank()) {
					cameras.add(new ChoreographyPlan.CameraPhrase(
						camera.timeSeconds(),
						camera.action(),
						camera.sectionIndex(),
						camera.subjectKind(),
						toId,
						camera.durationSeconds(),
						camera.framing(),
						camera.movement(),
						camera.easing(),
						camera.beatAligned(),
						camera.timingSnap(),
						camera.transition()
					));
				} else {
					cameras.add(new ChoreographyPlan.CameraPhrase(
						camera.timeSeconds(),
						camera.action(),
						camera.sectionIndex(),
						CameraSubjectKind.ALL_STAGE_OBJECTS.name(),
						"",
						camera.durationSeconds(),
						camera.framing(),
						camera.movement(),
						camera.easing(),
						camera.beatAligned(),
						camera.timingSnap(),
						camera.transition()
					));
				}
			} else {
				cameras.add(camera);
			}
		}

		List<ChoreographyVfx> vfxList = new ArrayList<>(plan.vfxPhrases().size());
		for (ChoreographyVfx vfx : plan.vfxPhrases()) {
			if (vfx instanceof ChoreographyVfx.ParticleBurst burst) {
				CameraSubject subject = burst.target();
				if (subject != null && isStageObjectKind(subject.kind()) && fromIds.contains(subject.refId())) {
					changed = true;
					CameraSubject rewritten = rewriteSubject(subject, fromIds, toId);
					vfxList.add(new ChoreographyVfx.ParticleBurst(
						burst.timeSeconds(),
						burst.name(),
						burst.particleType(),
						rewritten,
						burst.count(),
						burst.spread(),
						burst.speed(),
						burst.sectionIndex()
					));
					continue;
				}
			}
			vfxList.add(vfx);
		}

		if (!changed) return new PlanMutation(null, false);
		ChoreographyPlan remapped = new ChoreographyPlan(
			plan.sections(),
			roles,
			plan.motionPhrases(),
			cameras,
			vfxList,
			plan.densityCurve(),
			plan.sectionEdits(),
			plan.musicalStructure(),
			plan.spatialMotifPhrases(),
			phrases
		);
		timeline.setMetadata(ChoreographyPlanStore.KEY_PLAN, remapped);
		return new PlanMutation(plan, true);
	}

	private record ConfigMutation(@Nullable AutoMapConfig previous, boolean changed) {}

	private static ConfigMutation mutateConfig(
		@Nullable Timeline timeline,
		Set<String> fromIds,
		@Nullable String toId
	) {
		if (timeline == null) return new ConfigMutation(null, false);
		AutoMapConfig config = ChoreographyPlanStore.loadConfig(timeline);
		if (config == null) return new ConfigMutation(null, false);

		boolean changed = false;
		AutoMapConfig.Builder builder = AutoMapConfig.builder()
			.defaultHeightMultiplier(config.getDefaultHeightMultiplier())
			.minGapSeconds(config.getMinGapSeconds());
		for (AutoMapRule rule : config.getRules()) {
			String target = rule.getTargetObjectId();
			if (target != null && fromIds.contains(target)) {
				changed = true;
				String rewritten = toId != null && !toId.isBlank() ? toId : null;
				builder.rule(new AutoMapRule(
					rule.getFeatureKey(),
					rule.getMinEnergy(),
					rule.getAnimationTypeId(),
					rule.getDurationSeconds(),
					rule.isUseEnergyForHeight(),
					rule.getHeightMultiplier(),
					rule.getMinGapSeconds(),
					rewritten,
					rule.getTimingSnap()
				));
			} else {
				builder.rule(rule);
			}
		}
		for (Map.Entry<String, String> e : config.getTargetByNormalizedFeature().entrySet()) {
			if (fromIds.contains(e.getValue())) {
				changed = true;
				if (toId != null && !toId.isBlank()) {
					builder.targetForFeature(e.getKey(), toId);
				}
			} else {
				builder.targetForFeature(e.getKey(), e.getValue());
			}
		}
		if (!changed) return new ConfigMutation(null, false);
		timeline.setMetadata(ChoreographyPlanStore.KEY_CONFIG, builder.build());
		return new ConfigMutation(config, true);
	}

	private record SettingsMutation(@Nullable List<String> previous, boolean changed) {}

	private static SettingsMutation mutateAutoMapSettings(Set<String> fromIds, @Nullable String toId) {
		AutoMapSettings settings = AutoMapSettingsStore.current();
		List<String> previous = settings.getTargetObjectIds();
		boolean touched = false;
		List<String> next = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String id : previous) {
			if (fromIds.contains(id)) {
				touched = true;
				if (toId != null && !toId.isBlank() && seen.add(toId)) {
					next.add(toId);
				}
			} else if (seen.add(id)) {
				next.add(id);
			}
		}
		if (!touched) return new SettingsMutation(null, false);
		settings.setTargetObjectIds(next);
		return new SettingsMutation(previous, true);
	}

	// --- restore helpers / utilities ---

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

	private static void applyCameraSegmentParams(
		Timeline timeline,
		String clipId,
		String eventId,
		Map<String, Object> parameters
	) {
		Track track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent event = clip.getEvent(eventId);
		if (event == null) return;
		event.setParameters(parameters);
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

	private static TargetSet rewriteTargetSet(TargetSet targets, Set<String> fromIds, @Nullable String toId) {
		if (targets == null || targets.isEmpty()) return targets != null ? targets : TargetSet.of();
		List<String> next = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		boolean changed = false;
		for (String id : targets.objectIds()) {
			if (fromIds.contains(id)) {
				changed = true;
				if (toId != null && !toId.isBlank() && seen.add(toId)) {
					next.add(toId);
				}
			} else if (seen.add(id)) {
				next.add(id);
			}
		}
		return changed ? new TargetSet(next) : targets;
	}

	private static CameraSubject rewriteSubject(CameraSubject subject, Set<String> fromIds, @Nullable String toId) {
		if (subject == null || !isStageObjectKind(subject.kind()) || !fromIds.contains(subject.refId())) {
			return subject;
		}
		if (toId == null || toId.isBlank()) {
			return CameraSubject.allStageObjects();
		}
		return subject.kind() == CameraSubjectKind.ANIMATED_TARGET
			? CameraSubject.animatedTarget(toId)
			: CameraSubject.stageObject(toId);
	}

	private static boolean isStageObjectKind(CameraSubjectKind kind) {
		return kind == CameraSubjectKind.STAGE_OBJECT || kind == CameraSubjectKind.ANIMATED_TARGET;
	}

	private static boolean isStageObjectSubject(@Nullable String subjectKind) {
		if (subjectKind == null || subjectKind.isBlank()) return false;
		try {
			return isStageObjectKind(CameraSubjectKind.valueOf(subjectKind));
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private static String stringParam(@Nullable Object raw) {
		if (raw == null) return "";
		String value = String.valueOf(raw).trim();
		return value;
	}
}
