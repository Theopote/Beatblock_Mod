package com.beatblock.timeline.generation;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes animation StageEvents from a preset + target list (shared by drop + event library).
 * Committed inserts go through {@link TimelineDraftWriter#insertManualEvents}.
 */
public final class AnimationPresetEventWriter {

	public record WriteResult(List<String> eventIds, List<String> clipIds, boolean unbound) {
		public static final WriteResult EMPTY = new WriteResult(List.of(), List.of(), false);

		public WriteResult {
			eventIds = List.copyOf(eventIds != null ? eventIds : List.of());
			clipIds = List.copyOf(clipIds != null ? clipIds : List.of());
		}

		public int written() {
			return eventIds.size();
		}
	}

	private AnimationPresetEventWriter() {}

	public static WriteResult writePresetEvents(
		@Nullable Timeline timeline,
		@Nullable String trackId,
		@Nullable String presetId,
		double timeSeconds,
		@Nullable List<String> targetObjectIds
	) {
		if (timeline == null || trackId == null || trackId.isBlank() || presetId == null || presetId.isBlank()) {
			return WriteResult.EMPTY;
		}
		BlockInfluencePreset preset = BlockInfluencePresets.get(presetId);
		if (preset == null) {
			return WriteResult.EMPTY;
		}
		List<String> targets = targetObjectIds != null && !targetObjectIds.isEmpty()
			? targetObjectIds
			: List.of("");
		double duration = preset.getDefaultDurationSeconds();
		double t = Math.max(0.0, timeSeconds);
		List<TimelineAnimationEvent> events = new ArrayList<>(targets.size());
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
			events.add(new TimelineAnimationEvent(
				"",
				t,
				duration,
				presetId,
				target,
				1.0f,
				params.toParameterMap()
			));
		}
		TimelineDraftWriter.InsertionResult inserted =
			TimelineDraftWriter.insertManualEvents(timeline, trackId, events);
		if (inserted.isEmpty()) {
			return WriteResult.EMPTY;
		}
		return new WriteResult(inserted.eventIds(), inserted.clipIds(), anyUnbound);
	}

	/**
	 * Replace Timeline selection with the newly created events (all of them for multi-target
	 * so Properties can enter Batch Edit).
	 */
	public static void selectCreatedEvents(
		@Nullable SelectionState selection,
		@Nullable WriteResult result
	) {
		if (selection == null || result == null || result.written() <= 0) return;
		selection.clearEvents();
		selection.clearClips();
		for (String eventId : result.eventIds()) {
			selection.selectEvent(eventId);
		}
		selection.setRangeAnchorEventId(result.eventIds().getFirst());
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
