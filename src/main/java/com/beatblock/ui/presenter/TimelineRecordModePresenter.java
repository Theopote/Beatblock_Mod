package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.interaction.TimelineRecordModeHandler;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import imgui.flag.ImGuiKey;

import java.util.function.Supplier;

/** 实时录制模式：播放中按空格在当前播放头添加动画事件。 */
public final class TimelineRecordModePresenter {

	public record Feedback(String message, boolean success) {}

	private final Supplier<StageObjectSystem> stageObjectSystem;
	private Feedback lastFeedback;

	public TimelineRecordModePresenter() {
		this(() -> {
			var engine = BeatBlock.getContext().blockAnimationEngine();
			return engine != null ? engine.getStageObjectSystem() : null;
		});
	}

	TimelineRecordModePresenter(Supplier<StageObjectSystem> stageObjectSystem) {
		this.stageObjectSystem = stageObjectSystem;
	}

	public Feedback lastFeedback() {
		return lastFeedback;
	}

	public void clearFeedback() {
		lastFeedback = null;
	}

	public void handleKeyboard(TimelineEditor editor, TimelineToolbarState toolbarState, boolean musicPlaying) {
		if (editor == null || toolbarState == null || !toolbarState.isRecordMode()) {
			return;
		}
		if (ImGui.getIO().getWantCaptureKeyboard()) {
			return;
		}
		if (!ImGui.isKeyPressed(ImGuiKey.Space)) {
			return;
		}
		recordAtPlayhead(editor, toolbarState, musicPlaying);
	}

	public Feedback recordAtPlayhead(
		TimelineEditor editor,
		TimelineToolbarState toolbarState,
		boolean musicPlaying
	) {
		var outcome = TimelineRecordModeHandler.recordAtPlayhead(
			editor.getTimeline(),
			editor,
			toolbarState,
			stageObjectSystem.get(),
			musicPlaying
		);
		lastFeedback = new Feedback(localizeOutcome(outcome), outcome.success());
		return lastFeedback;
	}

	private static String localizeOutcome(TimelineRecordModeHandler.RecordOutcome outcome) {
		return switch (outcome.message()) {
			case "ok" -> BBTexts.get("beatblock.timeline.record.ok");
			case "not-playing" -> BBTexts.get("beatblock.timeline.record.not_playing");
			case "no-stage-object" -> BBTexts.get("beatblock.timeline.record.no_stage_object");
			case "duplicate-time" -> BBTexts.get("beatblock.timeline.record.duplicate");
			case "timeline-unavailable" -> BBTexts.get("beatblock.message.timeline_unavailable");
			default -> BBTexts.get("beatblock.timeline.record.failed");
		};
	}
}
