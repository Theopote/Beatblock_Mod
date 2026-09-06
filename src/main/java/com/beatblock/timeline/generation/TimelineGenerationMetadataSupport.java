package com.beatblock.timeline.generation;

import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 在 Timeline 事件参数中读写 {@link TimelineGenerationMetadata}。 */
public final class TimelineGenerationMetadataSupport {

	public static final String PARAM_ORIGIN = "eventOrigin";
	public static final String PARAM_GENERATOR_ID = "generatorId";
	public static final String PARAM_GENERATION_ID = "generationId";
	public static final String PARAM_SECTION_INDEX = "sectionIndex";
	public static final String PARAM_PHRASE_INDEX = "phraseIndex";
	public static final String PARAM_SOURCE_PLAN_ID = "sourcePlanId";

	/**
	 * Generation / provenance identity keys (core metadata + legacy aliases).
	 * <p>
	 * Shared by Paste remint, Event Template sanitization, and related policies.
	 * Does not include {@link #PARAM_ORIGIN} — remint rewrites origin via {@link #apply}.
	 */
	public static final Set<String> GENERATION_IDENTITY_KEYS = Set.of(
		PARAM_GENERATOR_ID,
		PARAM_GENERATION_ID,
		PARAM_SECTION_INDEX,
		PARAM_PHRASE_INDEX,
		PARAM_SOURCE_PLAN_ID,
		"generatedBy",
		"bindingRuleId",
		"bindingRuleName",
		"sourceFeature",
		"sourceSection",
		"sourcePhrase",
		"sourceStem",
		"mappingProfile",
		"bakedFromStepEventId"
	);

	/**
	 * Timeline / StageObject instance identity — never stored in reusable templates.
	 * Paste keeps {@code targetObject}; templates strip it and re-bind on apply.
	 */
	public static final Set<String> INSTANCE_IDENTITY_KEYS = Set.of(
		"targetObject",
		"eventId",
		"clipId",
		"trackId",
		"layerId",
		"layerBound"
	);

	private TimelineGenerationMetadataSupport() {}

	public static Map<String, Object> apply(
		@Nullable Map<String, Object> params,
		@Nullable TimelineGenerationMetadata metadata
	) {
		Map<String, Object> copy = params != null ? new HashMap<>(params) : new HashMap<>();
		TimelineGenerationMetadata resolved = metadata != null
			? metadata
			: TimelineGenerationMetadata.manual();
		copy.put(PARAM_ORIGIN, resolved.origin().name());
		if (resolved.origin().isGenerated() || resolved.origin().isImported()) {
			putIfPresent(copy, PARAM_GENERATOR_ID, resolved.generatorId());
			putIfPresent(copy, PARAM_GENERATION_ID, resolved.generationId());
			if (resolved.sectionIndex() >= 0) {
				copy.put(PARAM_SECTION_INDEX, resolved.sectionIndex());
			} else {
				copy.remove(PARAM_SECTION_INDEX);
			}
			if (resolved.phraseIndex() >= 0) {
				copy.put(PARAM_PHRASE_INDEX, resolved.phraseIndex());
			} else {
				copy.remove(PARAM_PHRASE_INDEX);
			}
			putIfPresent(copy, PARAM_SOURCE_PLAN_ID, resolved.sourcePlanId());
		} else {
			stripGenerationIdentity(copy);
		}
		return copy;
	}

	/** Removes all known generation / provenance identity keys from {@code params}. */
	public static void stripGenerationIdentity(@Nullable Map<String, Object> params) {
		if (params == null || params.isEmpty()) {
			return;
		}
		for (String key : GENERATION_IDENTITY_KEYS) {
			params.remove(key);
		}
	}

	/**
	 * Copy parameters and remint as {@link TimelineEventOrigin#MANUAL} content.
	 * <p>
	 * Same provenance policy used by Paste of GENERATED/IMPORTED events and by Event Templates.
	 * Preserves animation configuration; strips generation identity.
	 */
	public static Map<String, Object> remintAsManualCopy(@Nullable Map<String, Object> source) {
		Map<String, Object> copy = source != null ? new HashMap<>(source) : new HashMap<>();
		stripGenerationIdentity(copy);
		return apply(copy, TimelineGenerationMetadata.manual());
	}

	/**
	 * Parameters safe to persist as a reusable Event Template:
	 * remint as MANUAL and strip Timeline/StageObject instance identity.
	 */
	public static Map<String, Object> sanitizeForTemplate(@Nullable Map<String, Object> source) {
		Map<String, Object> copy = remintAsManualCopy(source);
		for (String key : INSTANCE_IDENTITY_KEYS) {
			copy.remove(key);
		}
		return copy;
	}

	public static Map<String, Object> sanitizeForTemplate(@Nullable TimelineAnimationEvent event) {
		if (event == null) {
			return remintAsManualCopy(Map.of());
		}
		return sanitizeForTemplate(AnimationEventParams.fromAnimationEvent(event).toParameterMap());
	}

	private static void putIfPresent(Map<String, Object> target, String key, String value) {
		if (value != null && !value.isBlank()) {
			target.put(key, value);
		} else {
			target.remove(key);
		}
	}
}
