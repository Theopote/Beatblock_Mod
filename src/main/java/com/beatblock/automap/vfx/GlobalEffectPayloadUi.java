package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

/** Payload-type-derived labels for Creator / Properties UI (no separate scope field). */
public final class GlobalEffectPayloadUi {

	private GlobalEffectPayloadUi() {
	}

	public static String scopeLabel(@Nullable GlobalEventPayload payload) {
		if (payload == null) {
			return "";
		}
		return switch (payload) {
			case GlobalEventPayload.EnvironmentLighting ignored ->
				BBTexts.get("beatblock.vfx_creator.scope.environment");
			case GlobalEventPayload.Lighting ignored ->
				BBTexts.get("beatblock.vfx_creator.scope.environment");
			case GlobalEventPayload.EnvironmentReset ignored ->
				BBTexts.get("beatblock.vfx_creator.scope.environment");
			case GlobalEventPayload.ScreenTint ignored ->
				BBTexts.get("beatblock.vfx_creator.scope.screen");
			case GlobalEventPayload.ScreenFlash ignored ->
				BBTexts.get("beatblock.vfx_creator.scope.screen");
			case GlobalEventPayload.LocalVisualWeather ignored ->
				BBTexts.get("beatblock.vfx_creator.scope.client");
			case GlobalEventPayload.ParticleBurst ignored ->
				BBTexts.get("beatblock.vfx_creator.scope.world_position");
			case GlobalEventPayload.AudioMix ignored ->
				BBTexts.get("beatblock.vfx_creator.scope.audio");
			case GlobalEventPayload.Generic ignored -> "";
		};
	}

	public static String scopeLabel(@Nullable GlobalEffectKind kind) {
		if (kind == null) {
			return "";
		}
		return switch (kind) {
			case ENVIRONMENT_LIGHTING -> BBTexts.get("beatblock.vfx_creator.scope.environment");
			case SCREEN_TINT, SCREEN_FLASH -> BBTexts.get("beatblock.vfx_creator.scope.screen");
			case WEATHER -> BBTexts.get("beatblock.vfx_creator.scope.client");
			case PARTICLE_BURST -> BBTexts.get("beatblock.vfx_creator.scope.world_position");
			case AUDIO_MIX -> BBTexts.get("beatblock.vfx_creator.scope.audio");
		};
	}

	public static String payloadTypeLabel(@Nullable GlobalEventPayload payload) {
		if (payload == null) {
			return "";
		}
		return switch (payload) {
			case GlobalEventPayload.EnvironmentLighting ignored ->
				BBTexts.get("beatblock.vfx_creator.payload.environment_lighting");
			case GlobalEventPayload.ScreenTint ignored ->
				BBTexts.get("beatblock.vfx_creator.payload.screen_tint");
			case GlobalEventPayload.Lighting ignored ->
				BBTexts.get("beatblock.vfx_creator.payload.environment_lighting");
			case GlobalEventPayload.EnvironmentReset ignored ->
				BBTexts.get("beatblock.vfx_creator.payload.environment_reset");
			case GlobalEventPayload.LocalVisualWeather ignored ->
				BBTexts.get("beatblock.vfx_creator.payload.weather");
			case GlobalEventPayload.ParticleBurst ignored ->
				BBTexts.get("beatblock.vfx_creator.payload.particle_burst");
			case GlobalEventPayload.ScreenFlash ignored ->
				BBTexts.get("beatblock.vfx_creator.payload.screen_flash");
			case GlobalEventPayload.AudioMix ignored ->
				BBTexts.get("beatblock.vfx_creator.payload.audio_mix");
			case GlobalEventPayload.Generic generic -> {
				String typeName = generic.typeName();
				yield typeName != null && !typeName.isBlank()
					? typeName
					: BBTexts.get("beatblock.vfx_creator.payload.generic");
			}
		};
	}
}
