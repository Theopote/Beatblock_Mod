package com.beatblock.ui.panels;

import com.beatblock.creator.CreationPreset;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.ProjectTemplatePresenter;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/**
 * 将旧「从模板新建」并入 {@link CreationPreset} 卡片选择：对当前时间线应用空白或绑定模板预设。
 */
public final class CreationPresetSelectDialog {

	private final ProjectTemplatePresenter templatePresenter;
	private final ImBoolean windowOpen = new ImBoolean(false);
	private CreationPreset selected = CreationPreset.BLANK;

	public CreationPresetSelectDialog() {
		this(PresenterFactories.projectTemplatePresenter());
	}

	CreationPresetSelectDialog(ProjectTemplatePresenter templatePresenter) {
		this.templatePresenter = templatePresenter;
	}

	public void openForTimelineApply() {
		selected = CreationPreset.BLANK;
		windowOpen.set(true);
	}

	public boolean isOpen() {
		return windowOpen.get();
	}

	public void render() {
		if (!windowOpen.get()) {
			return;
		}

		ImGui.setNextWindowSize(520f, 0);
		if (!ImGui.begin(
			BBTexts.get("beatblock.dialog.apply_creation_preset"),
			windowOpen,
			ImGuiWindowFlags.AlwaysAutoResize
		)) {
			ImGui.end();
			return;
		}

		try {
			ImGui.textWrapped(BBTexts.get("beatblock.preset.timeline_apply.desc"));
			ImGui.spacing();
			CreationPresetCardControls.renderPresetList(
				CreationPreset.TIMELINE_TEMPLATE_PRESETS,
				selected,
				preset -> selected = preset
			);
			ImGui.spacing();
			if (ImGui.button(BBTexts.get("beatblock.preset.timeline_apply.button") + "##applyPreset", -1f, 32f)) {
				applySelected();
			}
			ImGui.spacing();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##cancelPreset")) {
				windowOpen.set(false);
			}
		} finally {
			ImGui.end();
		}
	}

	private void applySelected() {
		var outcome = templatePresenter.apply(selected);
		if (outcome.success()) {
			ToastNotificationSystem.showSuccess(outcome.message());
			windowOpen.set(false);
		} else {
			ToastNotificationSystem.showError(outcome.message());
		}
	}
}
