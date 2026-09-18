package com.beatblock.timeline.rendering;

import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

/** 时间线工具栏：有编舞计划时显示 Section Edit 入口。 */
final class TimelineToolbarSectionEditControls {

	void renderInline(TimelineEditor editor) {
		if (editor == null) {
			return;
		}
		Timeline timeline = editor.getTimeline();
		if (!ChoreographyPlanStore.hasPlan(timeline)) {
			return;
		}

		if (ImGui.button(BBTexts.get("beatblock.section_edit.toolbar") + "##tlSectionEdit")) {
			SectionEditPopupCoordinator.requestOpen();
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.section_edit.toolbar.tooltip"));
		}
	}
}
