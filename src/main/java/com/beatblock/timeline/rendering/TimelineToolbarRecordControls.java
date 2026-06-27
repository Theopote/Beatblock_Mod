package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.ui.presenter.TimelineRecordModePresenter;
import imgui.ImGui;

/** 时间线工具栏：实时录制模式开关。 */
final class TimelineToolbarRecordControls {

	private final TimelineRecordModePresenter recordMode;

	TimelineToolbarRecordControls(TimelineRecordModePresenter recordMode) {
		this.recordMode = recordMode;
	}

	void renderInline(TimelineEditor editor, TimelineToolbarState toolbarState, boolean musicPlaying) {
		if (editor == null || toolbarState == null) return;

		boolean active = toolbarState.isRecordMode();
		if (active) {
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.75f, 0.15f, 0.15f, 1f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.85f, 0.2f, 0.2f, 1f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.6f, 0.1f, 0.1f, 1f);
		}

		IconButtonStyle.pushBeatBlockIconButton();
		if (ImGui.button(Icons.Play.RECORD + "##tlRecordMode", TimelineLayout.ROW_HEIGHT, TimelineLayout.ROW_HEIGHT)) {
			toolbarState.setRecordMode(!active);
		}
		IconButtonStyle.popBeatBlockIconButton();

		if (active) ImGui.popStyleColor(3);

		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.timeline.record.tooltip"));
		}

		var feedback = recordMode.lastFeedback();
		if (feedback != null && feedback.message() != null && !feedback.message().isBlank()) {
			TimelineToolbarImGui.nextItemInGroup();
			if (feedback.success()) {
				ImGui.textColored(0.4f, 1f, 0.4f, 1f, feedback.message());
			} else {
				ImGui.textColored(1f, 0.55f, 0.3f, 1f, feedback.message());
			}
		}
	}

	void renderCompact(TimelineEditor editor, TimelineToolbarState toolbarState, boolean musicPlaying) {
		ImGui.separator();
		ImGui.textDisabled(BBTexts.get("beatblock.timeline.record.section"));
		renderInline(editor, toolbarState, musicPlaying);
	}
}
