package com.beatblock.timeline.playback;

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
				positiveInt(params, "count", 1));
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
}