package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.TimelineToolbarViewPresenter;
import imgui.ImGui;
import imgui.type.ImInt;

final class TimelineToolbarViewControls {

	private final ImInt zoomComboIndex;
	private final TimelineToolbarTrackHeightControls trackHeight;

	TimelineToolbarViewControls(ImInt zoomComboIndex, TimelineToolbarTrackHeightControls trackHeight) {
		this.zoomComboIndex = zoomComboIndex;
		this.trackHeight = trackHeight;
	}

	void renderInline(TimelineEditor editor) {
		if (editor == null) return;
		renderZoom(editor, BBTexts.get("beatblock.timeline.zoom"), "");
		TimelineToolbarImGui.nextItemInGroup();
		renderFit(editor, BBTexts.get("beatblock.timeline.fit"), "", 130f);
		TimelineToolbarImGui.nextItemInGroup();
		trackHeight.renderInline(editor);
	}

	void renderCompact(TimelineEditor editor) {
		if (editor == null) return;
		ImGui.separator();
		ImGui.textDisabled(BBTexts.get("beatblock.timeline.view"));
		renderZoom(editor, BBTexts.get("beatblock.timeline.zoom") + "##tlMoreZoom", BBTexts.get("beatblock.timeline.zoom.tooltip"));
		renderFit(editor, BBTexts.get("beatblock.timeline.fit") + "##tlMoreFit", BBTexts.get("beatblock.timeline.fit.tooltip"), 16f);
		trackHeight.renderCompact(editor);
	}

	private void renderZoom(TimelineEditor editor, String label, String tooltipOverride) {
		zoomComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestZoom(editor.getViewState().getZoom()));
		String[] zoomLabels = TimelineToolbarViewPresenter.zoomPresetLabels();
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(zoomLabels));
		if (ImGui.combo(label, zoomComboIndex, zoomLabels)) {
			TimelineToolbarViewPresenter.applyZoomPreset(editor, zoomComboIndex.get());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(tooltipOverride.isEmpty() ? BBTexts.get("beatblock.timeline.zoom.tooltip") : tooltipOverride);
		}
	}

	private void renderFit(TimelineEditor editor, String label, String tooltipOverride, float widthPadding) {
		if (ImGui.button(label)) {
			TimelineToolbarViewPresenter.fitToDuration(
				editor, editor.getTimeline(), ImGui.getContentRegionAvailX() - widthPadding);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(tooltipOverride.isEmpty() ? BBTexts.get("beatblock.timeline.fit.tooltip") : tooltipOverride);
		}
	}
}
