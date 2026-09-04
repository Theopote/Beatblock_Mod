package com.beatblock.timeline.generation;

import com.beatblock.timeline.TimelineEventOrigin;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 自动生成内容的归属信息：回答「谁生成的、哪一批、对应计划哪一段」。
 * <p>
 * 持久化在事件 {@code parameters} 中（见 {@link TimelineGenerationMetadataSupport}）。
 */
public record TimelineGenerationMetadata(
	TimelineEventOrigin origin,
	String generatorId,
	String generationId,
	int sectionIndex,
	int phraseIndex,
	String sourcePlanId
) {
	public TimelineGenerationMetadata {
		origin = origin != null ? origin : TimelineEventOrigin.MANUAL;
		generatorId = generatorId != null ? generatorId : "";
		generationId = generationId != null ? generationId : "";
		sourcePlanId = sourcePlanId != null ? sourcePlanId : "";
	}

	public static TimelineGenerationMetadata manual() {
		return new TimelineGenerationMetadata(
			TimelineEventOrigin.MANUAL, "", "", -1, -1, "");
	}

	public static TimelineGenerationMetadata fromOrigin(TimelineEventOrigin origin) {
		if (origin != null && origin.isGenerated()) {
			return new TimelineGenerationMetadata(origin, "", "", -1, -1, "");
		}
		if (origin != null && origin.isImported()) {
			return new TimelineGenerationMetadata(origin, "", "", -1, -1, "");
		}
		return manual();
	}

	public boolean matches(ContentReplacePolicy policy) {
		if (policy == null) return false;
		return switch (policy) {
			case ContentReplacePolicy.Append ignored -> false;
			case ContentReplacePolicy.ReplaceAll ignored -> true;
			case ContentReplacePolicy.ReplaceGenerated ignored -> origin.isReplaceableByGeneration();
			case ContentReplacePolicy.ReplaceGenerator(var id, var includeLegacy) ->
				origin.isReplaceableByGeneration()
					&& (id.equals(generatorId)
					|| (includeLegacy && generatorId.isBlank()));
			case ContentReplacePolicy.ReplaceGeneration(var id) ->
				origin.isReplaceableByGeneration() && id.equals(generationId);
			case ContentReplacePolicy.ReplaceSection(var index) ->
				origin.isReplaceableByGeneration() && sectionIndex == index;
			case ContentReplacePolicy.ReplaceGeneratorSection(var id, var index) ->
				origin.isReplaceableByGeneration()
					&& id.equals(generatorId)
					&& sectionIndex == index;
		};
	}

	public static TimelineGenerationMetadata fromParameters(@Nullable Map<String, Object> params) {
		if (params == null || params.isEmpty()) {
			return manual();
		}
		TimelineEventOrigin origin = TimelineEventOrigin.fromValue(params.get(TimelineGenerationMetadataSupport.PARAM_ORIGIN));
		return new TimelineGenerationMetadata(
			origin,
			stringParam(params, TimelineGenerationMetadataSupport.PARAM_GENERATOR_ID),
			stringParam(params, TimelineGenerationMetadataSupport.PARAM_GENERATION_ID),
			intParam(params, TimelineGenerationMetadataSupport.PARAM_SECTION_INDEX, -1),
			intParam(params, TimelineGenerationMetadataSupport.PARAM_PHRASE_INDEX, -1),
			stringParam(params, TimelineGenerationMetadataSupport.PARAM_SOURCE_PLAN_ID)
		);
	}

	private static String stringParam(java.util.Map<String, Object> params, String key) {
		Object raw = params.get(key);
		return raw != null ? String.valueOf(raw).trim() : "";
	}

	private static int intParam(java.util.Map<String, Object> params, String key, int fallback) {
		Object raw = params.get(key);
		if (raw == null) return fallback;
		if (raw instanceof Number number) return number.intValue();
		try {
			return Integer.parseInt(String.valueOf(raw).trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
