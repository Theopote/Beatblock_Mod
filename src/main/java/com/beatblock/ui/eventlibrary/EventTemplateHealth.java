package com.beatblock.ui.eventlibrary;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.AnimationLibrary;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Assesses whether a saved {@link EventTemplate} is still applyable against the current catalog.
 */
public final class EventTemplateHealth {

	private EventTemplateHealth() {
	}

	public static EventTemplateItem assess(@Nullable EventTemplate template, @Nullable AnimationLibrary library) {
		if (template == null) {
			return new EventTemplateItem(
				new EventTemplate("", "", "", 0.35, 0.7f, Map.of()),
				EventTemplateStatus.INVALID_PARAMETERS,
				BBTexts.get("beatblock.event_library.health.invalid_parameters")
			);
		}
		String typeId = template.animationTypeId().trim();
		if (typeId.isEmpty()) {
			return item(template, EventTemplateStatus.INVALID_PARAMETERS,
				BBTexts.get("beatblock.event_library.health.invalid_parameters"));
		}

		Map<String, Object> params = template.parameters();
		String paramType = stringParam(params, "animationType");
		if (!paramType.isEmpty() && !paramType.equals(typeId)) {
			return item(template, EventTemplateStatus.INVALID_PARAMETERS,
				BBTexts.get("beatblock.event_library.health.type_mismatch", typeId, paramType));
		}

		AnimationLibrary catalog = library != null ? library : new AnimationLibrary();
		AnimationDefinition definition = catalog.get(typeId);
		if (definition == null) {
			return item(template, EventTemplateStatus.MISSING_ANIMATION,
				BBTexts.get("beatblock.event_library.health.missing_animation", typeId));
		}

		if (hasGenerationIdentity(params) || hasInstanceIdentity(params)) {
			return item(template, EventTemplateStatus.LEGACY,
				BBTexts.get("beatblock.event_library.health.legacy"));
		}

		return item(template, EventTemplateStatus.VALID, "");
	}

	private static EventTemplateItem item(EventTemplate template, EventTemplateStatus status, String warning) {
		return new EventTemplateItem(template, status, warning);
	}

	private static boolean hasGenerationIdentity(Map<String, Object> params) {
		if (params == null || params.isEmpty()) {
			return false;
		}
		for (String key : TimelineGenerationMetadataSupport.GENERATION_IDENTITY_KEYS) {
			if (params.containsKey(key)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasInstanceIdentity(Map<String, Object> params) {
		if (params == null || params.isEmpty()) {
			return false;
		}
		for (String key : TimelineGenerationMetadataSupport.INSTANCE_IDENTITY_KEYS) {
			if (params.containsKey(key)) {
				Object value = params.get(key);
				if (value == null) continue;
				if (value instanceof String s && s.isBlank()) continue;
				return true;
			}
		}
		return false;
	}

	private static String stringParam(Map<String, Object> params, String key) {
		if (params == null) return "";
		Object raw = params.get(key);
		return raw != null ? String.valueOf(raw).trim() : "";
	}
}
