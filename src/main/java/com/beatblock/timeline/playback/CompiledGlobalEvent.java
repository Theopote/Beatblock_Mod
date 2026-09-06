package com.beatblock.timeline.playback;

import com.beatblock.automap.vfx.GlobalEffectSemantics;

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

	/** Domain semantics: STATEFUL reconstructs on seek; IMPULSE does not. */
	public GlobalEffectSemantics effectSemantics() {
		return GlobalEffectSemantics.fromPayload(payload);
	}

	/** Maps {@link #effectSemantics()} into the PlaybackEngine filter vocabulary. */
	public PlaybackSemantics semantics() {
		return effectSemantics().toPlaybackSemantics();
	}

	public String typeName() {
		if (payload instanceof GlobalEventPayload.Generic g) {
			return g.typeName();
		}
		if (payload instanceof GlobalEventPayload.EnvironmentLighting) return "ENVIRONMENT_LIGHTING";
		if (payload instanceof GlobalEventPayload.EnvironmentReset) return "ENVIRONMENT_RESET";
		if (payload instanceof GlobalEventPayload.ScreenTint) return "SCREEN_TINT";
		if (payload instanceof GlobalEventPayload.Lighting) return "LIGHTING";
		if (payload instanceof GlobalEventPayload.LocalVisualWeather) return "LOCAL_VISUAL_WEATHER";
		if (payload instanceof GlobalEventPayload.ParticleBurst) return "PARTICLE";
		if (payload instanceof GlobalEventPayload.ScreenFlash) return "SCREEN_FLASH";
		if (payload instanceof GlobalEventPayload.AudioMix) return "AUDIO_MIX";
		return "SPECIAL";
	}

	public String name() {
		if (payload instanceof GlobalEventPayload.Generic g) return g.name();
		if (payload instanceof GlobalEventPayload.EnvironmentLighting p) return p.name();
		if (payload instanceof GlobalEventPayload.EnvironmentReset p) return p.name();
		if (payload instanceof GlobalEventPayload.ScreenTint p) return p.name();
		if (payload instanceof GlobalEventPayload.Lighting p) return p.name();
		if (payload instanceof GlobalEventPayload.LocalVisualWeather p) return p.name();
		if (payload instanceof GlobalEventPayload.ParticleBurst p) return p.name();
		if (payload instanceof GlobalEventPayload.ScreenFlash p) return p.name();
		if (payload instanceof GlobalEventPayload.AudioMix p) return p.name();
		return "";
	}
}
