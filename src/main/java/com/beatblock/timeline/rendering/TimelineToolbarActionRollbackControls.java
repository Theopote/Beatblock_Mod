package com.beatblock.timeline.rendering;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.TimelineToolbarConfigPresenter;
import imgui.ImGui;
import imgui.type.ImInt;

final class TimelineToolbarActionRollbackControls {

	private final TimelineToolbarConfigPresenter config;
	private final ImInt comboIndex;

	TimelineToolbarActionRollbackControls(TimelineToolbarConfigPresenter config, ImInt comboIndex) {
		this.config = config;
		this.comboIndex = comboIndex;
	}

	void renderInline() {
		render(false);
	}

	void renderCompact() {
		render(true);
	}

	private void render(boolean compactPopup) {
		config.ensureActionExecutionConfigLoaded();
		comboIndex.set(TimelineToolbarConfigPresenter.indexOfActionRollbackValue(config.readActionRollbackMode()));
		String label = compactPopup
			? BBTexts.get("beatblock.timeline.rollback") + "##tlMoreActionRollback"
			: BBTexts.get("beatblock.timeline.rollback");
		String[] rollbackLabels = TimelineToolbarConfigPresenter.actionRollbackLabels();
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(rollbackLabels));
		if (ImGui.combo(label, comboIndex, rollbackLabels)) {
			config.writeActionRollbackMode(TimelineToolbarConfigPresenter.actionRollbackValueAt(comboIndex.get()));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.rollback.tooltip"));
	}
}
