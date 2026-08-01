package com.beatblock.timeline.playback;

import java.util.Map;

/**
 * Strongly typed payload representing a compiled global / VFX track event.
 */
public sealed interface GlobalEventPayload {

	record Lighting(
		double intensity,
		float r,
		float g,
		float b,
		double durationSeconds
	) implements GlobalEventPayload {}

	record Weather(
		String weatherType,
		double transitionSeconds
	) implements GlobalEventPayload {}

	record ParticleBurst(
		String particleType,
		double x,
		double y,
		double z,
		int count
	) implements GlobalEventPayload {}

	record ScreenFlash(
		float r,
		float g,
		float b,
		double durationSeconds
	) implements GlobalEventPayload {}

	record AudioMix(
		String channel,
		float volume,
		double fadeSeconds
	) implements GlobalEventPayload {}

	record Generic(
		String typeName,
		String name,
		Map<String, Object> parameters
	) implements GlobalEventPayload {}
}
