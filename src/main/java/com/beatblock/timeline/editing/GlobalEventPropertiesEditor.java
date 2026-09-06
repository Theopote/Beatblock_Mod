package com.beatblock.timeline.editing;

import com.beatblock.automap.vfx.GlobalEffectKind;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.playback.GlobalEventPayloadCodec;
import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Global / VFX event property editing (no ImGui). Preserves typed payload fields via {@link GlobalEventPayloadCodec}. */
public final class GlobalEventPropertiesEditor {

	private GlobalEventPropertiesEditor() {
	}

	public sealed interface Result {
		record Ok(AnimationEventSnapshot snapshot) implements Result {}
		record Err(String message) implements Result {}
	}

	public record PayloadFormSnapshot(String time, GlobalEventPayload payload, GlobalEffectKind kind) {}

	public static PayloadFormSnapshot buildPayloadFormSnapshot(
		double timeSeconds,
		@Nullable Map<String, Object> parameters
	) {
		GlobalEventPayload payload = GlobalEventPayloadCodec.decode(parameters);
		return new PayloadFormSnapshot(
			String.valueOf(timeSeconds),
			payload,
			GlobalEffectKind.fromPayload(payload)
		);
	}

	public static Result buildUpdatedSnapshot(
		double timeSeconds,
		GlobalEventPayload payload,
		double clipStartSeconds,
		double clipEndSeconds,
		Map<String, Double> clipEventTimesById
	) {
		if (payload == null) {
			return new Result.Err(BBTexts.get("beatblock.vfx_creator.insert_failed"));
		}
		Map<String, Object> parameters = new LinkedHashMap<>(GlobalEventPayloadCodec.encode(payload));
		double clampedTime = TimelineEventMovePolicy.clipRange(clipStartSeconds, clipEndSeconds).clamp(timeSeconds);
		return new Result.Ok(new AnimationEventSnapshot(
			clampedTime,
			parameters,
			clipStartSeconds,
			clipEndSeconds,
			clipEventTimesById != null ? clipEventTimesById : Map.of(),
			Map.of(),
			clipEndSeconds
		));
	}

	/** @deprecated Legacy coarse {@code GlobalEventType} path — strips payload fields. Prefer payload snapshot API. */
	@Deprecated
	public static Result buildUpdatedSnapshot(
		double timeSeconds,
		com.beatblock.timeline.GlobalEventType type,
		String name,
		double clipStartSeconds,
		double clipEndSeconds,
		Map<String, Double> clipEventTimesById
	) {
		com.beatblock.timeline.GlobalEventType resolvedType = type != null ? type : com.beatblock.timeline.GlobalEventType.SPECIAL;
		String resolvedName = name != null ? name.trim() : "";
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("type", resolvedType.name());
		parameters.put("name", resolvedName);
		double clampedTime = TimelineEventMovePolicy.clipRange(clipStartSeconds, clipEndSeconds).clamp(timeSeconds);
		return new Result.Ok(new AnimationEventSnapshot(
			clampedTime,
			parameters,
			clipStartSeconds,
			clipEndSeconds,
			clipEventTimesById != null ? clipEventTimesById : Map.of(),
			Map.of(),
			clipEndSeconds
		));
	}

	public static com.beatblock.timeline.GlobalEventType parseType(String raw) {
		if (raw == null || raw.isBlank()) {
			return com.beatblock.timeline.GlobalEventType.SPECIAL;
		}
		try {
			return com.beatblock.timeline.GlobalEventType.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return com.beatblock.timeline.GlobalEventType.SPECIAL;
		}
	}

	public static @Nullable String validateTimeRange(double clipStart, double clipEnd) {
		if (clipEnd <= clipStart) {
			return BBTexts.get("beatblock.properties.clip.end_must_be_after_start");
		}
		return null;
	}
}
