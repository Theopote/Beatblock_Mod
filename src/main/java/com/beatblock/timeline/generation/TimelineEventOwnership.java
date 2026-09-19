package com.beatblock.timeline.generation;

import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Generated-content ownership helpers: promote / detach / lock.
 * <p>
 * {@code eventLocked} is independent of {@link TimelineEventOrigin} — locked content
 * is never removed by generation replace policies.
 */
public final class TimelineEventOwnership {

	public static final String PARAM_LOCKED = "eventLocked";

	private TimelineEventOwnership() {}

	public static boolean isLocked(@Nullable Map<String, Object> params) {
		if (params == null || params.isEmpty()) {
			return false;
		}
		Object raw = params.get(PARAM_LOCKED);
		if (raw instanceof Boolean bool) {
			return bool;
		}
		if (raw == null) {
			return false;
		}
		String s = String.valueOf(raw).trim();
		return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
	}

	public static boolean isLocked(@Nullable TimelineEvent event) {
		return event != null && isLocked(event.getParameters());
	}

	public static Map<String, Object> setLocked(@Nullable Map<String, Object> params, boolean locked) {
		Map<String, Object> copy = params != null ? new HashMap<>(params) : new HashMap<>();
		if (locked) {
			copy.put(PARAM_LOCKED, true);
		} else {
			copy.remove(PARAM_LOCKED);
		}
		return copy;
	}

	/**
	 * User edited a {@link TimelineEventOrigin#GENERATED} event → {@link TimelineEventOrigin#USER_EDITED}.
	 * Keeps generator / section provenance for “was AutoMap” UI. No-op for other origins.
	 */
	public static Map<String, Object> promoteOnUserEdit(@Nullable Map<String, Object> params) {
		Map<String, Object> copy = params != null ? new HashMap<>(params) : new HashMap<>();
		TimelineGenerationMetadata meta = TimelineGenerationMetadata.fromParameters(copy);
		if (!meta.origin().isGenerated()) {
			return copy;
		}
		TimelineGenerationMetadata promoted = new TimelineGenerationMetadata(
			TimelineEventOrigin.USER_EDITED,
			meta.generatorId(),
			meta.generationId(),
			meta.sectionIndex(),
			meta.phraseIndex(),
			meta.sourcePlanId()
		);
		return TimelineGenerationMetadataSupport.apply(copy, promoted);
	}

	/** Detach from AutoMap linkage: force {@link TimelineEventOrigin#USER_EDITED}, keep provenance. */
	public static Map<String, Object> detach(@Nullable Map<String, Object> params) {
		Map<String, Object> copy = params != null ? new HashMap<>(params) : new HashMap<>();
		TimelineGenerationMetadata meta = TimelineGenerationMetadata.fromParameters(copy);
		TimelineGenerationMetadata detached = new TimelineGenerationMetadata(
			TimelineEventOrigin.USER_EDITED,
			meta.generatorId(),
			meta.generationId(),
			meta.sectionIndex(),
			meta.phraseIndex(),
			meta.sourcePlanId()
		);
		return TimelineGenerationMetadataSupport.apply(copy, detached);
	}

	public static boolean isProtectedFromGeneration(@Nullable TimelineGenerationMetadata metadata) {
		if (metadata == null) {
			return true;
		}
		return !metadata.origin().isReplaceableByGeneration();
	}

	public static boolean isProtectedFromGeneration(@Nullable Map<String, Object> params) {
		if (isLocked(params)) {
			return true;
		}
		return isProtectedFromGeneration(TimelineGenerationMetadata.fromParameters(params));
	}

	public static boolean isProtectedFromGeneration(@Nullable TimelineEvent event) {
		return event != null && isProtectedFromGeneration(event.getParameters());
	}

	/** Apply promoted/detached/locked parameter map onto a live event. */
	public static void applyParameters(@Nullable TimelineEvent event, @Nullable Map<String, Object> params) {
		if (event == null || params == null) {
			return;
		}
		event.setParameters(params);
	}
}
