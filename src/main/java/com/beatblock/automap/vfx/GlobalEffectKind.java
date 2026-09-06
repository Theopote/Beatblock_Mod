package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.GlobalEventPayload;
import org.jspecify.annotations.Nullable;

/**
 * Creator-facing effect kinds — 1:1 with {@link GlobalEventPayload} variants (not coarse {@code GlobalEventType}).
 */
public enum GlobalEffectKind {
	ENVIRONMENT_LIGHTING,
	SCREEN_TINT,
	WEATHER,
	PARTICLE_BURST,
	SCREEN_FLASH,
	AUDIO_MIX;

	public GlobalEventPayload defaultPayload(@Nullable String name) {
		String resolved = name != null && !name.isBlank() ? name.trim() : defaultName();
		return switch (this) {
			case ENVIRONMENT_LIGHTING -> new GlobalEventPayload.EnvironmentLighting(
				resolved, 1.0, 1f, 1f, 1f, 1.0);
			case SCREEN_TINT -> new GlobalEventPayload.ScreenTint(
				resolved, 0.65, 1f, 1f, 1f, 2.0);
			case WEATHER -> new GlobalEventPayload.LocalVisualWeather(resolved, "clear", 1.0);
			case PARTICLE_BURST -> new GlobalEventPayload.ParticleBurst(
				resolved, "minecraft:poof", 0, 64, 0, 24,
				GlobalEventPayload.ParticleBurst.DEFAULT_SPREAD,
				GlobalEventPayload.ParticleBurst.DEFAULT_SPEED);
			case SCREEN_FLASH -> new GlobalEventPayload.ScreenFlash(resolved, 1f, 1f, 1f, 0.15);
			case AUDIO_MIX -> new GlobalEventPayload.AudioMix(resolved, "master", 1f, 0.5);
		};
	}

	public String defaultName() {
		return switch (this) {
			case ENVIRONMENT_LIGHTING -> "Environment Lighting";
			case SCREEN_TINT -> "Screen Tint";
			case WEATHER -> "Weather";
			case PARTICLE_BURST -> "Particles";
			case SCREEN_FLASH -> "Screen Flash";
			case AUDIO_MIX -> "Audio Mix";
		};
	}

	public static GlobalEffectKind fromPayload(@Nullable GlobalEventPayload payload) {
		if (payload == null) {
			return SCREEN_TINT;
		}
		return switch (payload) {
			case GlobalEventPayload.EnvironmentLighting ignored -> ENVIRONMENT_LIGHTING;
			case GlobalEventPayload.ScreenTint ignored -> SCREEN_TINT;
			case GlobalEventPayload.Lighting ignored -> ENVIRONMENT_LIGHTING;
			case GlobalEventPayload.EnvironmentReset ignored -> ENVIRONMENT_LIGHTING;
			case GlobalEventPayload.LocalVisualWeather ignored -> WEATHER;
			case GlobalEventPayload.ParticleBurst ignored -> PARTICLE_BURST;
			case GlobalEventPayload.ScreenFlash ignored -> SCREEN_FLASH;
			case GlobalEventPayload.AudioMix ignored -> AUDIO_MIX;
			case GlobalEventPayload.Generic generic -> fromTypeName(generic.typeName());
		};
	}

	private static GlobalEffectKind fromTypeName(@Nullable String typeName) {
		if (typeName == null || typeName.isBlank()) {
			return SCREEN_TINT;
		}
		return switch (typeName.trim().toUpperCase().replace('-', '_').replace(' ', '_')) {
			case "ENVIRONMENT_LIGHTING", "LIGHTING", "ENVIRONMENT_RESET", "RESET_ENVIRONMENT" -> ENVIRONMENT_LIGHTING;
			case "SCREEN_TINT", "OVERLAY_TINT" -> SCREEN_TINT;
			case "WEATHER", "LOCAL_VISUAL_WEATHER" -> WEATHER;
			case "PARTICLE", "PARTICLE_BURST" -> PARTICLE_BURST;
			case "SCREEN_FLASH" -> SCREEN_FLASH;
			case "AUDIO_MIX" -> AUDIO_MIX;
			default -> SCREEN_TINT;
		};
	}
}
