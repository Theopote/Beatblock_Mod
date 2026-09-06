package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.playback.PlaybackSemantics;
import org.jspecify.annotations.Nullable;

/**
 * Playback semantics for typed global VFX (seek reconstruct).
 *
 * <ul>
 *   <li>{@link #CONTINUOUS_STATE} — sticky until a later cue of the same kind</li>
 *   <li>{@link #FINITE_ENVELOPE} — active only inside {@code [start, start+duration)}</li>
 *   <li>{@link #IMPULSE} — fire-and-forget; never reconstructed on seek</li>
 * </ul>
 */
public enum GlobalEffectSemantics {
	CONTINUOUS_STATE,
	FINITE_ENVELOPE,
	IMPULSE;

	/** Whether seek/reconstruct should re-apply this cue when it is still active. */
	public boolean reconstructOnSeek() {
		return this != IMPULSE;
	}

	public PlaybackSemantics toPlaybackSemantics() {
		return reconstructOnSeek() ? PlaybackSemantics.STATEFUL : PlaybackSemantics.TRANSIENT;
	}

	public static GlobalEffectSemantics fromPayload(@Nullable GlobalEventPayload payload) {
		if (payload == null) {
			return IMPULSE;
		}
		return switch (payload) {
			case GlobalEventPayload.EnvironmentLighting ignored -> CONTINUOUS_STATE;
			case GlobalEventPayload.Lighting ignored -> CONTINUOUS_STATE;
			case GlobalEventPayload.LocalVisualWeather ignored -> CONTINUOUS_STATE;
			case GlobalEventPayload.AudioMix ignored -> CONTINUOUS_STATE;
			case GlobalEventPayload.ScreenTint ignored -> FINITE_ENVELOPE;
			case GlobalEventPayload.ScreenFlash ignored -> FINITE_ENVELOPE;
			case GlobalEventPayload.ParticleBurst ignored -> IMPULSE;
			case GlobalEventPayload.Generic ignored -> IMPULSE;
		};
	}
}
