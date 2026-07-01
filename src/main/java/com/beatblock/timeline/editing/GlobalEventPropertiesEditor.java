package com.beatblock.timeline.editing;

import com.beatblock.timeline.GlobalEventType;
import com.beatblock.ui.i18n.BBTexts;

import java.util.HashMap;
import java.util.Map;

/** 全局事件属性编辑（无 ImGui 依赖）。 */
public final class GlobalEventPropertiesEditor {

	private GlobalEventPropertiesEditor() {
	}

	public sealed interface Result {
		record Ok(AnimationEventSnapshot snapshot) implements Result {}
		record Err(String message) implements Result {}
	}

	public static Result buildUpdatedSnapshot(
		double timeSeconds,
		GlobalEventType type,
		String name,
		double clipStartSeconds,
		double clipEndSeconds,
		Map<String, Double> clipEventTimesById
	) {
		GlobalEventType resolvedType = type != null ? type : GlobalEventType.SPECIAL;
		String resolvedName = name != null ? name.trim() : "";
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("type", resolvedType.name());
		parameters.put("name", resolvedName);
		double clampedTime = Math.max(clipStartSeconds, Math.min(clipEndSeconds, Math.max(0.0, timeSeconds)));
		return new Result.Ok(new AnimationEventSnapshot(
			clampedTime,
			parameters,
			clipStartSeconds,
			clipEndSeconds,
			clipEventTimesById != null ? clipEventTimesById : Map.of()
		));
	}

	public static GlobalEventType parseType(String raw) {
		if (raw == null || raw.isBlank()) {
			return GlobalEventType.SPECIAL;
		}
		try {
			return GlobalEventType.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return GlobalEventType.SPECIAL;
		}
	}

	public static String validateTimeRange(double clipStart, double clipEnd) {
		if (clipEnd <= clipStart) {
			return BBTexts.get("beatblock.properties.clip.end_must_be_after_start");
		}
		return null;
	}
}
