package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.TimelineToolbarViewPresenter;
import imgui.ImGui;

final class TimelineToolbarTrackHeightControls {

	private static final float SLIDER_WIDTH = 120f;
	private static final float SLIDER_WIDTH_COMPACT = 180f;

	void renderInline(TimelineEditor editor) {
		if (editor == null) return;
		var trackHeight = TimelineToolbarViewPresenter.trackHeightViewState(editor);
		float[] value = new float[] { trackHeight.current() };

		ImGui.setNextItemWidth(SLIDER_WIDTH);
		if (ImGui.sliderFloat(BBTexts.get("beatblock.timeline.track_h"), value, trackHeight.min(), trackHeight.max(), "%.0f px")) {
			TimelineToolbarViewPresenter.setTrackHeight(editor, value[0]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.track_height.tooltip"));
		TimelineToolbarImGui.nextItemInGroup();
		if (ImGui.button(BBTexts.get("beatblock.timeline.track_height_reset") + "##tlTrackHReset")) {
			TimelineToolbarViewPresenter.resetTrackHeight(editor);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.track_height_reset.tooltip"));
	}

	void renderCompact(TimelineEditor editor) {
		if (editor == null) return;
		var trackHeight = TimelineToolbarViewPresenter.trackHeightViewState(editor);
		float[] value = new float[] { trackHeight.current() };

		ImGui.separator();
		ImGui.textDisabled(BBTexts.get("beatblock.timeline.track_height"));
		ImGui.setNextItemWidth(SLIDER_WIDTH_COMPACT);
		if (ImGui.sliderFloat(BBTexts.get("beatblock.timeline.track_h") + "##tlMoreTrackH", value, trackHeight.min(), trackHeight.max(), "%.0f px")) {
			TimelineToolbarViewPresenter.setTrackHeight(editor, value[0]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.track_height.tooltip"));
		if (ImGui.button(BBTexts.get("beatblock.timeline.track_height_reset") + "##tlMoreTrackHReset")) {
			TimelineToolbarViewPresenter.resetTrackHeight(editor);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.track_height_reset.tooltip"));
	}
}
