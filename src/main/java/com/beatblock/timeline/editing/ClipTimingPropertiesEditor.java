package com.beatblock.timeline.editing;

import com.beatblock.timeline.Timeline;
import com.beatblock.ui.i18n.BBTexts;

import java.util.Map;

/** 通用片段时间与 Timeline 元数据编辑（音频、建造图层等）。 */
public final class ClipTimingPropertiesEditor {

	private ClipTimingPropertiesEditor() {
	}

	public sealed interface Result {
		record Ok(AnimationEventSnapshot snapshot) implements Result {}
		record Err(String message) implements Result {}
	}

	public static Result buildSnapshot(
		double oldClipStart,
		double newStart,
		double newEnd,
		Map<String, Double> existingEventTimes,
		Map<String, String> metadataUpdates,
		Timeline timeline
	) {
		if (newEnd <= newStart) {
			return new Result.Err(BBTexts.get("beatblock.properties.clip.end_must_be_after_start"));
		}
		Map<String, Double> shiftedTimes = existingEventTimes != null
			? CameraEventPropertiesEditor.shiftClipEventTimes(
				existingEventTimes.entrySet(), oldClipStart, newStart, newEnd)
			: Map.of();
		double timelineDuration = timeline != null
			? Math.max(timeline.getDurationSeconds(), newEnd)
			: newEnd;
		double primaryTime = shiftedTimes.isEmpty() ? newStart : shiftedTimes.values().iterator().next();
		return new Result.Ok(new AnimationEventSnapshot(
			primaryTime,
			Map.of(),
			newStart,
			newEnd,
			shiftedTimes,
			metadataUpdates != null ? metadataUpdates : Map.of(),
			timelineDuration
		));
	}
}
