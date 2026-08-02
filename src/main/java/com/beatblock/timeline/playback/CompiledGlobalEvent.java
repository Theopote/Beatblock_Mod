package com.beatblock.timeline.playback;

import java.util.Objects;

/**
 * Immutable global / VFX cue snapshot (Phase C).
 * Covers the timeline global track (lighting, special, stage cues).
 */
public record CompiledGlobalEvent(
	String id,
	double timeSeconds,
	GlobalEventPayload payload
) {
	public CompiledGlobalEvent {
		id = id != null ? id : "";
		Objects.requireNonNull(payload, "payload");
		if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
			throw new IllegalArgumentException("timeSeconds must be finite and non-negative: " + timeSeconds);
		}
	}

	public PlaybackSemantics semantics() {
		if (payload instanceof GlobalEventPayload.EnvironmentLighting
			|| payload instanceof GlobalEventPayload.ScreenTint
			|| payload instanceof GlobalEventPayload.Lighting
			|| payload instanceof GlobalEventPayload.Weather
			|| payload instanceof GlobalEventPayload.AudioMix) {
			return PlaybackSemantics.STATEFUL;
		}
		return PlaybackSemantics.TRANSIENT;
	}
	public String typeName() {
		if (payload instanceof GlobalEventPayload.Generic g) {
			return g.typeName();
		}
		if (payload instanceof GlobalEventPayload.EnvironmentLighting) return "ENVIRONMENT_LIGHTING";
		if (payload instanceof GlobalEventPayload.ScreenTint) return "SCREEN_TINT";
		if (payload instanceof GlobalEventPayload.Lighting) return "LIGHTING";
		if (payload instanceof GlobalEventPayload.Weather) return "WEATHER";
		if (payload instanceof GlobalEventPayload.ParticleBurst) return "PARTICLE";
		if (payload instanceof GlobalEventPayload.ScreenFlash) return "SCREEN_FLASH";
		if (payload instanceof GlobalEventPayload.AudioMix) return "AUDIO_MIX";
		return "SPECIAL";
	}

	public String name() {
		if (payload instanceof GlobalEventPayload.Generic g) return g.name();
		if (payload instanceof GlobalEventPayload.EnvironmentLighting p) return p.name();
		if (payload instanceof GlobalEventPayload.ScreenTint p) return p.name();
		if (payload instanceof GlobalEventPayload.Lighting p) return p.name();
		if (payload instanceof GlobalEventPayload.Weather p) return p.name();
		if (payload instanceof GlobalEventPayload.ParticleBurst p) return p.name();
		if (payload instanceof GlobalEventPayload.ScreenFlash p) return p.name();
		if (payload instanceof GlobalEventPayload.AudioMix p) return p.name();
		return "";
	}
}
