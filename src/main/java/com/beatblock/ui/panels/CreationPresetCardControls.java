package com.beatblock.ui.panels;

import com.beatblock.creator.CreationPreset;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import imgui.flag.ImGuiStyleVar;

import java.util.function.Consumer;

/** CreationPreset 大卡片列表，供向导与预设对话框复用。 */
final class CreationPresetCardControls {

	private CreationPresetCardControls() {
	}

	static void renderPresetList(
		CreationPreset[] presets,
		CreationPreset selected,
		Consumer<CreationPreset> onSelect
	) {
		if (presets == null || presets.length == 0) {
			return;
		}
		for (CreationPreset preset : presets) {
			renderPresetCard(preset, selected, onSelect);
			ImGui.dummy(0f, 4f);
		}
	}

	static void renderPresetCard(
		CreationPreset preset,
		CreationPreset selected,
		Consumer<CreationPreset> onSelect
	) {
		boolean isSelected = preset == selected;
		if (isSelected) {
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Border, 0.55f, 0.48f, 0.88f, 1f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ChildBg, 0.22f, 0.20f, 0.32f, 1f);
		} else {
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Border, 0.28f, 0.26f, 0.34f, 1f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ChildBg, 0.13f, 0.12f, 0.17f, 1f);
		}
		ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 0f);
		ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, isSelected ? 2f : 1f);

		float cardW = ImGui.getContentRegionAvailX();
		float cardH = ImGui.getTextLineHeightWithSpacing() * 2f + 14f;
		ImGui.beginChild("##preset_" + preset.name(), cardW, cardH, true);
		ImGui.popStyleVar(2);
		ImGui.popStyleColor(2);

		try {
			if (ImGui.isWindowHovered() && ImGui.isMouseClicked(0) && onSelect != null) {
				onSelect.accept(preset);
			}
			if (ImGui.isWindowHovered()) {
				ImGui.setTooltip(BBTexts.get(preset.tooltipKey()));
			}

			ImGui.text(BBTexts.get(preset.titleKey()));
			ImGui.textDisabled(BBTexts.get(preset.cardDescKey()));
		} finally {
			ImGui.endChild();
		}
	}
}
