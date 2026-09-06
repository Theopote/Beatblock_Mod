package com.beatblock.ui.presenter;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.i18n.BBTexts;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 动画库面板业务逻辑：把 {@link BlockInfluencePreset} 应用到已选动画事件或时间线。
 */
public final class AnimationLibraryPanelPresenter {

	public record ViewState(
		boolean editorReady,
		boolean canApplyToSelection,
		int selectedAnimationEventCount,
		String statusMessage
	) {}

	public record ApplyOutcome(boolean success, String message) {}

	private final EventPropertiesPresenter eventPropertiesPresenter;
	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;

	private String statusMessage = "";

	public AnimationLibraryPanelPresenter(
		EventPropertiesPresenter eventPropertiesPresenter,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor
	) {
		this.eventPropertiesPresenter = eventPropertiesPresenter;
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
	}

	public ViewState viewState() {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return new ViewState(false, false, 0, statusMessage);
		}
		int count = eventPropertiesPresenter.countSelectedAnimationEvents(tl, editor.getSelectionState());
		return new ViewState(true, count > 0, count, statusMessage);
	}

	public ApplyOutcome applyPresetToSelection(String presetId) {
		BlockInfluencePreset preset = BlockInfluencePresets.get(presetId);
		if (preset == null) {
			return fail(BBTexts.get("beatblock.animation_library.preset_missing"));
		}
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		SelectionState selectionState = editor.getSelectionState();
		CommandManager commandManager = editor.getCommandManager();
		EventPropertiesPresenter.BatchEditOutcome outcome = eventPropertiesPresenter.applyBatchAnimationEdit(
			tl,
			selectionState,
			commandManager,
			EventPropertiesPresenter.BatchAnimationEditRequest.replaceAnimation(
				preset.getId(),
				preset.getDefaultDurationSeconds()
			)
		);
		if (outcome.success()) {
			statusMessage = BBTexts.get(
				"beatblock.animation_library.applied",
				preset.getDisplayName(),
				outcome.updatedCount()
			);
			return new ApplyOutcome(true, statusMessage);
		}
		String error = outcome.errorMessage();
		return fail(error != null && !error.isBlank() ? error : BBTexts.get("beatblock.animation_library.apply_failed"));
	}

	private ApplyOutcome fail(String message) {
		statusMessage = message;
		return new ApplyOutcome(false, message);
	}
}
