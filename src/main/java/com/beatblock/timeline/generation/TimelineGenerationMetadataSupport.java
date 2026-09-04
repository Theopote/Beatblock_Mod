package com.beatblock.timeline.generation;

import com.beatblock.timeline.TimelineEventOrigin;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** 在 Timeline 事件参数中读写 {@link TimelineGenerationMetadata}。 */
public final class TimelineGenerationMetadataSupport {

	public static final String PARAM_ORIGIN = "eventOrigin";
	public static final String PARAM_GENERATOR_ID = "generatorId";
	public static final String PARAM_GENERATION_ID = "generationId";
	public static final String PARAM_SECTION_INDEX = "sectionIndex";
	public static final String PARAM_PHRASE_INDEX = "phraseIndex";
	public static final String PARAM_SOURCE_PLAN_ID = "sourcePlanId";

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
			copy.remove(PARAM_GENERATOR_ID);
			copy.remove(PARAM_GENERATION_ID);
			copy.remove(PARAM_SECTION_INDEX);
			copy.remove(PARAM_PHRASE_INDEX);
			copy.remove(PARAM_SOURCE_PLAN_ID);
		}
		return copy;
	}

	private static void putIfPresent(Map<String, Object> target, String key, String value) {
		if (value != null && !value.isBlank()) {
			target.put(key, value);
		} else {
			target.remove(key);
		}
	}
}
