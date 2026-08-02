package com.beatblock.timeline.playback;

import java.util.Map;

/** Strongly typed payload representing a compiled global / VFX track event. */
public sealed interface GlobalEventPayload {
	/** World/environment lighting intent. Execution requires a world-capable backend. */
	record EnvironmentLighting(String name, double intensity, float r, float g, float b, double durationSeconds)
		implements GlobalEventPayload { public EnvironmentLighting { name = name != null ? name : ""; } }
	/** Editor/screen overlay tint; does not modify Minecraft world lighting. */
	record ScreenTint(String name, double intensity, float r, float g, float b, double durationSeconds)
		implements GlobalEventPayload { public ScreenTint { name = name != null ? name : ""; } }
	/** @deprecated Use EnvironmentLighting or ScreenTint to state the intended capability. */
	@Deprecated
	record Lighting(String name, double intensity, float r, float g, float b, double durationSeconds)
		implements GlobalEventPayload { public Lighting { name = name != null ? name : ""; } }
	/** Client-only presentation weather; does not change authoritative or saved world weather. */
	record LocalVisualWeather(String name, String weatherType, double transitionSeconds)
		implements GlobalEventPayload { public LocalVisualWeather { name = name != null ? name : ""; } }
	record ParticleBurst(String name, String particleType, double x, double y, double z, int count)
		implements GlobalEventPayload { public ParticleBurst { name = name != null ? name : ""; } }
	record ScreenFlash(String name, float r, float g, float b, double durationSeconds)
		implements GlobalEventPayload { public ScreenFlash { name = name != null ? name : ""; } }
	record AudioMix(String name, String channel, float volume, double fadeSeconds)
		implements GlobalEventPayload { public AudioMix { name = name != null ? name : ""; } }
	record Generic(String typeName, String name, Map<String, Object> parameters)
		implements GlobalEventPayload {}
}