package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Named multi-cue environment looks (Lighting + Weather + Tint, …).
 * Applying one preset is one user action → one Undo via {@link com.beatblock.timeline.command.CompositeCommand}.
 */
public record EnvironmentPreset(
	String id,
	String labelKey,
	List<GlobalEventPayload> components
) {
	public EnvironmentPreset {
		id = id != null ? id.trim().toLowerCase(Locale.ROOT) : "";
		labelKey = labelKey != null ? labelKey : "";
		components = List.copyOf(components != null ? components : List.of());
		if (id.isBlank()) {
			throw new IllegalArgumentException("preset id required");
		}
		if (components.isEmpty()) {
			throw new IllegalArgumentException("preset requires at least one component");
		}
	}

	public String displayName() {
		String label = BBTexts.get(labelKey);
		return label != null && !label.isBlank() && !label.equals(labelKey) ? label : id;
	}

	public int componentCount() {
		return components.size();
	}

	public static EnvironmentPreset nightPerformance() {
		return new EnvironmentPreset(
			"night_performance",
			"beatblock.vfx_creator.preset.night_performance",
			List.of(
				new GlobalEventPayload.EnvironmentLighting(
					"Night Performance Lighting", 0.4, 0.35f, 0.4f, 0.75f, 0),
				new GlobalEventPayload.ScreenTint(
					"Night Performance Tint", 0.45, 0.1f, 0.15f, 0.45f, 0)
			)
		);
	}

	public static EnvironmentPreset storm() {
		return new EnvironmentPreset(
			"storm",
			"beatblock.vfx_creator.preset.storm",
			List.of(
				new GlobalEventPayload.EnvironmentLighting(
					"Storm Lighting", 0.45, 0.55f, 0.6f, 0.7f, 0),
				new GlobalEventPayload.LocalVisualWeather(
					"Storm Weather", "rain", 1.5),
				new GlobalEventPayload.ScreenTint(
					"Storm Tint", 0.35, 0.2f, 0.25f, 0.35f, 0)
			)
		);
	}

	public static EnvironmentPreset warmSunset() {
		return new EnvironmentPreset(
			"warm_sunset",
			"beatblock.vfx_creator.preset.warm_sunset",
			List.of(
				new GlobalEventPayload.EnvironmentLighting(
					"Warm Sunset Lighting", 0.85, 1f, 0.55f, 0.25f, 0),
				new GlobalEventPayload.ScreenTint(
					"Warm Sunset Tint", 0.4, 1f, 0.45f, 0.2f, 0)
			)
		);
	}

	public static EnvironmentPreset concertFlash() {
		return new EnvironmentPreset(
			"concert_flash",
			"beatblock.vfx_creator.preset.concert_flash",
			List.of(
				new GlobalEventPayload.ScreenTint(
					"Concert Flash Tint", 0.25, 1f, 1f, 1f, 0.4),
				new GlobalEventPayload.ScreenFlash(
					"Concert Flash", 1f, 1f, 1f, 0.2),
				new GlobalEventPayload.EnvironmentLighting(
					"Concert Flash Lighting", 1.2, 1f, 1f, 1f, 0.35)
			)
		);
	}

	public static List<EnvironmentPreset> all() {
		return List.of(nightPerformance(), storm(), warmSunset(), concertFlash());
	}

	public static Optional<EnvironmentPreset> find(@Nullable String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		String key = id.trim().toLowerCase(Locale.ROOT);
		for (EnvironmentPreset preset : all()) {
			if (preset.id().equals(key)) {
				return Optional.of(preset);
			}
		}
		return Optional.empty();
	}
}
