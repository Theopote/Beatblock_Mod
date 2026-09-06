package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventPayload;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Global VFX active at a timeline time — seek reconstruct + scrub/export sampling.
 * Impulse particles are omitted; finite envelopes include ScreenTint and ScreenFlash.
 */
public record ActiveGlobalEffectState(
	@Nullable CompiledGlobalEvent environmentLighting,
	@Nullable CompiledGlobalEvent screenTint,
	@Nullable CompiledGlobalEvent screenFlash,
	@Nullable CompiledGlobalEvent weather,
	@Nullable CompiledGlobalEvent audioMix
) {
	public static ActiveGlobalEffectState empty() {
		return new ActiveGlobalEffectState(null, null, null, null, null);
	}

	public static ActiveGlobalEffectState resolve(
		@Nullable List<CompiledGlobalEvent> events,
		double timelineTimeSeconds
	) {
		if (!Double.isFinite(timelineTimeSeconds)) {
			throw new IllegalArgumentException("timelineTimeSeconds must be finite");
		}
		if (events == null || events.isEmpty()) {
			return empty();
		}

		CompiledGlobalEvent lighting = null;
		CompiledGlobalEvent tint = null;
		CompiledGlobalEvent flash = null;
		CompiledGlobalEvent weather = null;
		CompiledGlobalEvent audio = null;

		for (CompiledGlobalEvent event : events) {
			if (event == null || event.timeSeconds() > timelineTimeSeconds) {
				break;
			}
			switch (event.payload()) {
				case GlobalEventPayload.EnvironmentLighting ignored -> lighting = event;
				case GlobalEventPayload.Lighting ignored -> lighting = event;
				case GlobalEventPayload.LocalVisualWeather ignored -> weather = event;
				case GlobalEventPayload.AudioMix ignored -> audio = event;
				case GlobalEventPayload.ScreenTint tintPayload ->
					tint = GlobalEffectActiveWindow.inDurationWindow(
						event.timeSeconds(), tintPayload.durationSeconds(), timelineTimeSeconds)
						? event : null;
				case GlobalEventPayload.ScreenFlash flashPayload ->
					flash = GlobalEffectActiveWindow.inDurationWindow(
						event.timeSeconds(), Math.max(0.01, flashPayload.durationSeconds()), timelineTimeSeconds)
						? event : null;
				default -> {
				}
			}
		}
		return new ActiveGlobalEffectState(lighting, tint, flash, weather, audio);
	}
}
