package com.beatblock.timeline.playback;

import com.beatblock.automap.camera.CameraSubjectKind;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Single decoder shared by global-event validation and compilation. */
public final class GlobalEventPayloadCodec {

	private GlobalEventPayloadCodec() {}

	public static GlobalEventPayload decode(@Nullable Map<String, Object> parameters) {
		Map<String, Object> params = parameters != null ? parameters : Map.of();
		String type = normalizeType(params.get("type"));
		return switch (type) {
			case "LIGHTING", "ENVIRONMENT_LIGHTING" -> new GlobalEventPayload.EnvironmentLighting(
				string(params, "name", ""),
				number(params, "intensity", 1.0),
				(float) number(params, "r", 1.0),
				(float) number(params, "g", 1.0),
				(float) number(params, "b", 1.0),
				nonNegative(params, "durationSeconds", 0.0));
			case "SCREEN_TINT", "OVERLAY_TINT" -> new GlobalEventPayload.ScreenTint(
				string(params, "name", ""),
				number(params, "intensity", 1.0),
				(float) number(params, "r", 1.0),
				(float) number(params, "g", 1.0),
				(float) number(params, "b", 1.0),
				nonNegative(params, "durationSeconds", 0.0));
			case "WEATHER", "LOCAL_VISUAL_WEATHER" -> new GlobalEventPayload.LocalVisualWeather(
				string(params, "name", ""),
				string(params, "weatherType", "clear"),
				nonNegative(params, "transitionSeconds", 0.0));
			case "PARTICLE", "PARTICLE_BURST" -> new GlobalEventPayload.ParticleBurst(
				string(params, "name", ""),
				string(params, "particleType", "minecraft:poof"),
				number(params, "x", 0.0),
				number(params, "y", 0.0),
				number(params, "z", 0.0),
				positiveInt(params, "count", 1),
				nonNegative(params, "spread", GlobalEventPayload.ParticleBurst.DEFAULT_SPREAD),
				nonNegative(params, "speed", GlobalEventPayload.ParticleBurst.DEFAULT_SPEED),
				parseFollowSubjectKind(params),
				string(params, "followSubjectRef", ""));
			case "SCREEN_FLASH" -> new GlobalEventPayload.ScreenFlash(
				string(params, "name", ""),
				(float) number(params, "r", 1.0),
				(float) number(params, "g", 1.0),
				(float) number(params, "b", 1.0),
				nonNegative(params, "durationSeconds", 0.1));
			case "AUDIO_MIX" -> new GlobalEventPayload.AudioMix(
				string(params, "name", ""),
				string(params, "channel", "master"),
				(float) number(params, "volume", 1.0),
				nonNegative(params, "fadeSeconds", 0.0));
			default -> new GlobalEventPayload.Generic(
				type, string(params, "name", ""), Map.copyOf(new LinkedHashMap<>(params)));
		};
	}

	public static Map<String, Object> encode(GlobalEventPayload payload) {
		if (payload == null) return Map.of("type", "SPECIAL", "name", "");
		Map<String, Object> params = new LinkedHashMap<>();
		switch (payload) {
			case GlobalEventPayload.EnvironmentLighting value -> {
				params.put("type", "ENVIRONMENT_LIGHTING");
				params.put("name", value.name());
				params.put("intensity", value.intensity());
				params.put("r", value.r());
				params.put("g", value.g());
				params.put("b", value.b());
				params.put("durationSeconds", value.durationSeconds());
			}
			case GlobalEventPayload.ScreenTint value -> {
				params.put("type", "SCREEN_TINT");
				params.put("name", value.name());
				params.put("intensity", value.intensity());
				params.put("r", value.r());
				params.put("g", value.g());
				params.put("b", value.b());
				params.put("durationSeconds", value.durationSeconds());
			}
			case GlobalEventPayload.Lighting value -> {
				params.put("type", "LIGHTING");
				params.put("name", value.name());
				params.put("intensity", value.intensity());
				params.put("r", value.r());
				params.put("g", value.g());
				params.put("b", value.b());
				params.put("durationSeconds", value.durationSeconds());
			}
			case GlobalEventPayload.LocalVisualWeather value -> {
				params.put("type", "LOCAL_VISUAL_WEATHER");
				params.put("name", value.name());
				params.put("weatherType", value.weatherType());
				params.put("transitionSeconds", value.transitionSeconds());
			}
			case GlobalEventPayload.ParticleBurst value -> {
				params.put("type", "PARTICLE_BURST");
				params.put("name", value.name());
				params.put("particleType", value.particleType());
				params.put("x", value.x());
				params.put("y", value.y());
				params.put("z", value.z());
				params.put("count", value.count());
				params.put("spread", value.spread());
				params.put("speed", value.speed());
				if (value.followSubjectKind() != null) {
					params.put("followSubjectKind", value.followSubjectKind().name());
					if (!value.followSubjectRef().isBlank()) {
						params.put("followSubjectRef", value.followSubjectRef());
					}
				}
			}
			case GlobalEventPayload.ScreenFlash value -> {
				params.put("type", "SCREEN_FLASH");
				params.put("name", value.name());
				params.put("r", value.r());
				params.put("g", value.g());
				params.put("b", value.b());
				params.put("durationSeconds", value.durationSeconds());
			}
			case GlobalEventPayload.AudioMix value -> {
				params.put("type", "AUDIO_MIX");
				params.put("name", value.name());
				params.put("channel", value.channel());
				params.put("volume", value.volume());
				params.put("fadeSeconds", value.fadeSeconds());
			}
			case GlobalEventPayload.Generic value -> {
				params.put("type", value.typeName());
				params.put("name", value.name());
				params.putAll(value.parameters());
			}
		}
		return Map.copyOf(params);
	}

	static String normalizeType(@Nullable Object raw) {
		String value = raw != null ? String.valueOf(raw).trim() : "SPECIAL";
		if (value.isEmpty()) value = "SPECIAL";
		return value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
	}

	private static String string(Map<String, Object> params, String key, String fallback) {
		Object raw = params.get(key);
		if (raw == null) return fallback;
		String value = String.valueOf(raw).trim();
		return value.isEmpty() ? fallback : value;
	}

	private static double number(Map<String, Object> params, String key, double fallback) {
		Object raw = params.get(key);
		if (raw == null) return fallback;
		double value;
		try {
			value = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(raw));
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid global parameter " + key + ": " + raw, ex);
		}
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("Non-finite global parameter " + key + ": " + raw);
		}
		return value;
	}

	private static double nonNegative(Map<String, Object> params, String key, double fallback) {
		double value = number(params, key, fallback);
		if (value < 0) throw new IllegalArgumentException("Negative global parameter " + key + ": " + value);
		return value;
	}

	private static int positiveInt(Map<String, Object> params, String key, int fallback) {
		double value = number(params, key, fallback);
		if (value <= 0 || value > Integer.MAX_VALUE || value != Math.rint(value)) {
			throw new IllegalArgumentException("Invalid positive integer global parameter " + key + ": " + value);
		}
		return (int) value;
	}

	private static @Nullable CameraSubjectKind parseFollowSubjectKind(Map<String, Object> params) {
		String raw = string(params, "followSubjectKind", "");
		if (raw.isBlank()) {
			return null;
		}
		try {
			return CameraSubjectKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}