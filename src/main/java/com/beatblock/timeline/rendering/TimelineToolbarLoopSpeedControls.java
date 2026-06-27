package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.TimelineToolbarViewPresenter;
import com.beatblock.ui.presenter.TimelineTransportPresenter;
import imgui.ImGui;
import imgui.type.ImInt;

final class TimelineToolbarLoopSpeedControls {

	private final TimelineTransportPresenter transport;
	private final ImInt speedComboIndex;
	private final TimelineToolbarActionRollbackControls actionRollback;

	TimelineToolbarLoopSpeedControls(
		TimelineTransportPresenter transport,
		ImInt speedComboIndex,
		TimelineToolbarActionRollbackControls actionRollback
	) {
		this.transport = transport;
		this.speedComboIndex = speedComboIndex;
		this.actionRollback = actionRollback;
	}

	void renderInline(
		TimelineEditor editor,
		TimelineToolbarState toolbarState,
		double seekStep,
		double now
	) {
		renderLoopButtons(toolbarState, now, seekStep, "", "", "");
		TimelineToolbarImGui.nextItemInGroup();
		renderSpeed(editor, BBTexts.get("beatblock.timeline.speed"), "");
		TimelineToolbarImGui.nextItemInGroup();
		actionRollback.renderInline();
	}

	void renderCompact(TimelineEditor editor, TimelineToolbarState toolbarState, double seekStep, double now) {
		ImGui.textDisabled(BBTexts.get("beatblock.timeline.loop_speed"));
		renderLoopButtons(toolbarState, now, seekStep, "##tlMoreIn", "##tlMoreOut", "##tlMoreClr");
		ImGui.sameLine();
		renderSpeed(editor, BBTexts.get("beatblock.timeline.speed") + "##tlMoreSpeed", BBTexts.get("beatblock.timeline.speed.tooltip"));
		actionRollback.renderCompact();
	}

	private void renderLoopButtons(
		TimelineToolbarState toolbarState,
		double now,
		double seekStep,
		String inSuffix,
		String outSuffix,
		String clrSuffix
	) {
		if (ImGui.button(BBTexts.get("beatblock.timeline.loop_in") + inSuffix)) {
			transport.setLoopInAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.loop_in.tooltip"));
		if (inSuffix.isEmpty()) TimelineToolbarImGui.nextItemInGroup();
		else ImGui.sameLine();

		if (ImGui.button(BBTexts.get("beatblock.timeline.loop_out") + outSuffix)) {
			transport.setLoopOutAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.loop_out.tooltip"));
		if (inSuffix.isEmpty()) TimelineToolbarImGui.nextItemInGroup();
		else ImGui.sameLine();

		if (ImGui.button(BBTexts.get("beatblock.timeline.loop_clear") + clrSuffix)) {
			transport.clearLoopRange(toolbarState);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.loop_clear.tooltip"));
	}

	private void renderSpeed(TimelineEditor editor, String label, String tooltipOverride) {
		speedComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestSpeed(transport.currentPlaybackSpeed(editor)));
		String[] speedLabels = TimelineToolbarViewPresenter.speedLabels();
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(speedLabels));
		if (ImGui.combo(label, speedComboIndex, speedLabels)) {
			TimelineToolbarViewPresenter.applySpeedPreset(editor, transport, speedComboIndex.get());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(tooltipOverride.isEmpty() ? BBTexts.get("beatblock.timeline.speed.tooltip") : tooltipOverride);
		}
	}
}
