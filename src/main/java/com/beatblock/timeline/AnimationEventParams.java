package com.beatblock.timeline;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 动画时间线事件的强类型核心参数；扩展字段仍通过 {@link #extensions()} 以 Map 承载。
 * <p>
 * 与 {@link TimelineEvent#getParameters()} 的键名保持兼容（持久化 / .osc / 播放器读取）。
 */
public record AnimationEventParams(
	@NonNull TimelineAnimationActionMode actionMode,
	@NonNull String animationType,
	@NonNull String targetObject,
	float energy,
	double durationSeconds,
	@NonNull TimelineEventOrigin eventOrigin,
	@NonNull Map<String, Object> extensions
) {

	public static final Set<String> CORE_PARAMETER_KEYS = Set.of(
		"actionMode",
		"mode",
		"animationType",
		"targetObject",
		"energy",
		"durationSeconds",
		"eventOrigin"
	);

	public AnimationEventParams {
		actionMode = actionMode != null ? actionMode : TimelineAnimationActionMode.ANIMATE;
		animationType = animationType != null ? animationType : "";
		targetObject = targetObject != null ? targetObject : "";
		energy = Math.max(0f, Math.min(1f, energy));
		durationSeconds = Math.max(0.01, durationSeconds);
		eventOrigin = eventOrigin != null ? eventOrigin : TimelineEventOrigin.MANUAL;
		extensions = extensions != null ? Map.copyOf(extensions) : Map.of();
	}

	public static @NonNull AnimationEventParams fromAnimationEvent(@NonNull TimelineAnimationEvent event) {
		Map<String, Object> extensions = new HashMap<>();
		for (Map.Entry<String, Object> entry : event.getParameters().entrySet()) {
			if (!isCoreParameterKey(entry.getKey())) {
				extensions.put(entry.getKey(), entry.getValue());
			}
		}
		return new AnimationEventParams(
			event.getActionMode(),
			event.getAnimationTypeId(),
			event.getTargetObjectId(),
			event.getEnergy(),
			event.getDurationSeconds(),
			event.getEventOrigin(),
			extensions
		);
	}

	public static @NonNull AnimationEventParams fromParameterMap(@Nullable Map<String, Object> params) {
		Map<String, Object> source = params != null ? params : Map.of();
		Map<String, Object> extensions = new HashMap<>();
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			if (!isCoreParameterKey(entry.getKey())) {
				extensions.put(entry.getKey(), entry.getValue());
			}
		}
		Object modeRaw = source.get("actionMode");
		if (modeRaw == null) {
			modeRaw = source.get("mode");
		}
		float energy = 1f;
		Object energyRaw = source.get("energy");
		if (energyRaw instanceof Number number) {
			energy = number.floatValue();
		}
		double duration = 0.01;
		Object durationRaw = source.get("durationSeconds");
		if (durationRaw instanceof Number number) {
			duration = number.doubleValue();
		}
		return new AnimationEventParams(
			TimelineAnimationActionMode.fromValue(modeRaw),
			stringValue(source.get("animationType")),
			stringValue(source.get("targetObject")),
			energy,
			duration,
			TimelineEventOrigin.fromValue(source.get("eventOrigin")),
			extensions
		);
	}

	public static boolean isCoreParameterKey(@Nullable String key) {
		return key != null && CORE_PARAMETER_KEYS.contains(key);
	}

	public @NonNull Map<String, Object> toParameterMap() {
		Map<String, Object> map = new HashMap<>(extensions);
		map.put("actionMode", actionMode.name());
		map.put("mode", actionMode.name());
		map.put("animationType", animationType);
		map.put("targetObject", targetObject);
		map.put("energy", energy);
		map.put("durationSeconds", durationSeconds);
		map.put("eventOrigin", eventOrigin.name());
		return map;
	}

	private static String stringValue(@Nullable Object raw) {
		if (raw == null) {
			return "";
		}
		return String.valueOf(raw).trim();
	}
}
