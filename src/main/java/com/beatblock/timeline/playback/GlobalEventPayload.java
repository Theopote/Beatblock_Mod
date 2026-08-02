package com.beatblock.timeline.playback;

import java.util.Map;

/** Strongly typed payload representing a compiled global / VFX track event. */
public sealed interface GlobalEventPayload {
	record Lighting(String name, double intensity, float r, float g, float b, double durationSeconds)
		implements GlobalEventPayload { public Lighting { name = name != null ? name : ""; } }
	record Weather(String name, String weatherType, double transitionSeconds)
		implements GlobalEventPayload { public Weather { name = name != null ? name : ""; } }
	record ParticleBurst(String name, String particleType, double x, double y, double z, int count)
		implements GlobalEventPayload { public ParticleBurst { name = name != null ? name : ""; } }
	record ScreenFlash(String name, float r, float g, float b, double durationSeconds)
		implements GlobalEventPayload { public ScreenFlash { name = name != null ? name : ""; } }
	record AudioMix(String name, String channel, float volume, double fadeSeconds)
		implements GlobalEventPayload { public AudioMix { name = name != null ? name : ""; } }
	record Generic(String typeName, String name, Map<String, Object> parameters)
		implements GlobalEventPayload {}
}