package com.beatblock.timeline.generation;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Writes animation StageEvents from a preset + target list (shared by drop + event library).
 */
public final class AnimationPresetEventWriter {

	public record WriteResult(int written, boolean unbound) {}

	private AnimationPresetEventWriter() {}

	public static WriteResult writePresetEvents(
		@Nullable Timeline timeline,
		@Nullable String trackId,
		@Nullable String presetId,
		double timeSeconds,
		@Nullable List<String> targetObjectIds
	) {
		if (timeline == null || trackId == null || trackId.isBlank() || presetId == null || presetId.isBlank()) {
			return new WriteResult(0, false);
		}
		BlockInfluencePreset preset = BlockInfluencePresets.get(presetId);
		if (preset == null) {
			return new WriteResult(0, false);
		}
		List<String> targets = targetObjectIds != null && !targetObjectIds.isEmpty()
			? targetObjectIds
			: List.of("");
		double duration = preset.getDefaultDurationSeconds();
		double t = Math.max(0.0, timeSeconds);
		int written = 0;
		boolean anyUnbound = false;
		for (String targetObjectId : targets) {
			String target = targetObjectId != null ? targetObjectId : "";
			if (target.isBlank()) {
				anyUnbound = true;
			}
			AnimationEventParams params = new AnimationEventParams(
				TimelineAnimationActionMode.ANIMATE,
				presetId,
				target,
				1.0f,
				duration,
				TimelineEventOrigin.MANUAL,
				Map.of()
			);
			TimelineAnimationEvent event = new TimelineAnimationEvent(
				"",
				t,
				duration,
				presetId,
				target,
				1.0f,
				params.toParameterMap()
			);
			if (TimelineDraftWriter.writeEvent(timeline, trackId, event, TimelineEventOrigin.MANUAL)) {
				written++;
			}
		}
		if (written > 0) {
			timeline.sortAll();
		}
		return new WriteResult(written, anyUnbound && written > 0);
	}

	public static void toastWriteResult(
		@Nullable String presetDisplayName,
		WriteResult result
	) {
		if (result == null || result.written() <= 0) {
			ToastNotificationSystem.showError(BBTexts.get("beatblock.animation_library.drop_failed"));
			return;
		}
		String name = presetDisplayName != null ? presetDisplayName : "";
		if (result.unbound()) {
			ToastNotificationSystem.showWarning(BBTexts.get(
				"beatblock.animation_library.dropped_unbound",
				name
			));
		} else if (result.written() > 1) {
			ToastNotificationSystem.showSuccess(BBTexts.get(
				"beatblock.animation_library.dropped_multi",
				name,
				result.written()
			));
		} else {
			ToastNotificationSystem.showSuccess(BBTexts.get(
				"beatblock.animation_library.dropped",
				name
			));
		}
	}
}
