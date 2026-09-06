package com.beatblock.timeline.editing;

import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Replace-animation semantics for Animation Library {@code Apply}:
 * keep target / energy / origin; set new animation type + duration as {@code ANIMATE};
 * strip animation-specific and incompatible spatial/step parameters.
 */
public final class AnimationTypeReplace {

	/**
	 * Provenance / metadata keys retained across animation-type replacement.
	 * Sourced from {@link TimelineGenerationMetadataSupport#GENERATION_IDENTITY_KEYS}
	 * so Replace / Paste / Template share one key catalog (Replace keeps; remint strips).
	 */
	public static final Set<String> PRESERVED_EXTENSION_KEYS =
		TimelineGenerationMetadataSupport.GENERATION_IDENTITY_KEYS;

	private AnimationTypeReplace() {}

	/**
	 * Build params after replacing the animation type.
	 * <p>
	 * Keeps target, energy, origin. Forces {@link TimelineAnimationActionMode#ANIMATE}.
	 * Clears trajectory / step / spatial / build / flash / other non-preserved extensions
	 * (e.g. {@code meteorHeight}, {@code impactThreshold}, {@code dispatchModel}).
	 */
	public static AnimationEventParams apply(
		@Nullable AnimationEventParams current,
		@Nullable String newAnimationId,
		double newDurationSeconds
	) {
		AnimationEventParams source = current != null
			? current
			: new AnimationEventParams(
				TimelineAnimationActionMode.ANIMATE,
				"",
				"",
				1f,
				0.35,
				TimelineEventOrigin.MANUAL,
				Map.of()
			);
		String animationId = newAnimationId != null ? newAnimationId.trim() : "";
		return new AnimationEventParams(
			TimelineAnimationActionMode.ANIMATE,
			animationId,
			source.targetObject(),
			source.energy(),
			newDurationSeconds,
			source.eventOrigin(),
			preserveExtensions(source.extensions())
		);
	}

	static Map<String, Object> preserveExtensions(@Nullable Map<String, Object> extensions) {
		if (extensions == null || extensions.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> kept = new HashMap<>();
		for (Map.Entry<String, Object> entry : extensions.entrySet()) {
			String key = entry.getKey();
			if (key == null || key.isBlank()) continue;
			if (isPreservedExtensionKey(key)) {
				kept.put(key, entry.getValue());
			}
		}
		return Map.copyOf(kept);
	}

	public static boolean isPreservedExtensionKey(@Nullable String key) {
		if (key == null || key.isBlank()) return false;
		return PRESERVED_EXTENSION_KEYS.contains(key) || key.startsWith("bb.");
	}
}
