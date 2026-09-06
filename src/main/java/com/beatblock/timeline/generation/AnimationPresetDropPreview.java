package com.beatblock.timeline.generation;

import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;

/**
 * Drag preview for Animation Library → Timeline drops:
 * {@code Preset + Target + Time = StageEvent}.
 */
public final class AnimationPresetDropPreview {

	public record Lines(String presetLine, String targetLine, String timeLine) {
		public Lines {
			presetLine = presetLine != null ? presetLine : "";
			targetLine = targetLine != null ? targetLine : "";
			timeLine = timeLine != null ? timeLine : "";
		}
	}

	private AnimationPresetDropPreview() {}

	/**
	 * @param nameResolver maps StageObject id → display name; may return null (falls back to id)
	 */
	public static Lines format(
		@Nullable String presetDisplayName,
		AnimationDropTargetResolver.@Nullable Result targets,
		double timeSeconds,
		@Nullable Function<String, String> nameResolver
	) {
		String preset = presetDisplayName != null && !presetDisplayName.isBlank()
			? presetDisplayName.trim()
			: BBTexts.get("beatblock.animation_library.drop_preview.preset_unknown");

		AnimationDropTargetResolver.Result resolved = targets != null
			? targets
			: new AnimationDropTargetResolver.Result(AnimationDropTargetResolver.Mode.UNBOUND, java.util.List.of());

		String targetLine = switch (resolved.mode()) {
			case UNBOUND -> BBTexts.get("beatblock.animation_library.drop_preview.target_unbound");
			case MULTI -> BBTexts.get(
				"beatblock.animation_library.drop_preview.target_multi",
				resolved.targetObjectIds().size()
			);
			case SINGLE -> BBTexts.get(
				"beatblock.animation_library.drop_preview.target",
				displayNameFor(resolved.targetObjectIds().isEmpty()
					? ""
					: resolved.targetObjectIds().getFirst(), nameResolver)
			);
		};

		String timeLine = BBTexts.get(
			"beatblock.animation_library.drop_preview.time",
			String.format(Locale.ROOT, "%.2f", Math.max(0.0, timeSeconds))
		);
		return new Lines(preset, targetLine, timeLine);
	}

	public static void renderTooltip(@Nullable Lines lines) {
		if (lines == null) return;
		ImGui.beginTooltip();
		ImGui.text(lines.presetLine());
		ImGui.text(lines.targetLine());
		ImGui.text(lines.timeLine());
		ImGui.endTooltip();
	}

	/** Resolve StageObject display name from runtime registry; falls back to id. */
	public static @Nullable String resolveStageObjectName(@Nullable String targetId) {
		if (targetId == null || targetId.isBlank()) return null;
		try {
			var engine = com.beatblock.BeatBlock.getContext().blockAnimationEngine();
			if (engine == null) return null;
			var obj = engine.getStageObjectSystem().get(targetId);
			return obj != null ? obj.getName() : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static String displayNameFor(
		@Nullable String targetId,
		@Nullable Function<String, String> nameResolver
	) {
		if (targetId == null || targetId.isBlank()) {
			return BBTexts.get("beatblock.animation_library.drop_preview.unbound_label");
		}
		if (nameResolver != null) {
			String name = nameResolver.apply(targetId);
			if (name != null && !name.isBlank()) {
				return name.trim();
			}
		}
		return targetId.trim();
	}
}
