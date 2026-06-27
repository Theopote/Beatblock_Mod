package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCond;

/** 工具栏与添加音频弹窗。 */
final class AudioAnalysisToolbarControls {

	private AudioAnalysisToolbarControls() {
	}

	static void renderToolbar(AudioAnalysisPanelHost host) {
		AudioAnalysisPanelUiState state = host.uiState();
		IconButtonStyle.pushBeatBlockIconButton();
		if (ImGui.button(AudioAnalysisPanelImGui.iconLabel(Icons.Action.ADD, "+") + "##AddAudio",
			AudioAnalysisPanelImGui.ICON_BTN, AudioAnalysisPanelImGui.ICON_BTN)) {
			state.importPath().set("");
			ImGui.openPopup("##AddAudioPopup");
		}
		if (ImGui.isItemHovered()) AudioAnalysisPanelImGui.setTooltipWithDefaultFont();

		ImGui.sameLine();

		String detailIcon = state.detailExpanded() ? Icons.Layout.LEFT_COLLAPSE : Icons.Layout.RIGHT_EXPAND;
		String detailFallback = state.detailExpanded() ? "<" : ">";
		if (ImGui.button(AudioAnalysisPanelImGui.iconLabel(detailIcon, detailFallback) + "##detail",
			AudioAnalysisPanelImGui.ICON_BTN, AudioAnalysisPanelImGui.ICON_BTN)) {
			state.toggleDetailExpanded();
		}
		IconButtonStyle.popBeatBlockIconButton();
		if (ImGui.isItemHovered()) ImGui.setTooltip(state.detailExpanded()
			? BBTexts.get("beatblock.audio.collapse_detail")
			: BBTexts.get("beatblock.audio.expand_detail"));

		if (host.presenter().isAnalyzerAvailable()) {
			ImGui.sameLine();
			ImGui.spacing();
			ImGui.sameLine();
			state.demucsToggle().set(host.presenter().isUseDemucs());
			if (ImGui.checkbox(BBTexts.get("beatblock.audio.demucs_default") + "##demucsToggle", state.demucsToggle())) {
				host.presenter().setUseDemucs(state.demucsToggle().get());
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.audio.demucs.tooltip"));
			}
		}

		renderAddPopup(host);

		ImGui.separator();
	}

	static void renderAddPopup(AudioAnalysisPanelHost host) {
		AudioAnalysisPanelUiState state = host.uiState();
		ImGui.setNextWindowSize(460f, 0f, ImGuiCond.Always);
		if (!ImGui.beginPopup("##AddAudioPopup")) return;

		ImGui.text(BBTexts.get("beatblock.audio.select_file"));
		if (ImGui.button(BBTexts.get("beatblock.audio.browse") + "##browseAudio", 120f, 0f)) {
			String chosenPath = host.chooseAudioFilePath();
			if (chosenPath != null && !chosenPath.isBlank()) {
				state.importPath().set(chosenPath);
				host.handleIncomingAudioPath(chosenPath);
				ImGui.closeCurrentPopup();
			}
		}

		ImGui.spacing();
		if (state.importPath().get().isBlank()) {
			ImGui.textDisabled(BBTexts.get("beatblock.audio.no_file_selected"));
		} else {
			AudioAnalysisPanelImGui.renderCollapsedInlineValue(state, state.importPath().get(), "##importPathPreview", null);
		}

		ImGui.spacing();
		ImGui.textDisabled(BBTexts.get("beatblock.audio.supported_formats"));
		ImGui.textDisabled(BBTexts.get("beatblock.audio.auto_start_hint"));
		ImGui.spacing();

		boolean add = ImGui.button(BBTexts.get("beatblock.audio.add_and_analyze") + "##add", 150f, 0f);
		ImGui.sameLine();
		boolean cancel = ImGui.button(BBTexts.get("beatblock.common.cancel") + "##cancel");

		if (add) {
			String path = state.importPath().get().trim();
			if (!path.isEmpty()) {
				host.handleIncomingAudioPath(path);
			}
			ImGui.closeCurrentPopup();
		}
		if (cancel) {
			ImGui.closeCurrentPopup();
		}

		ImGui.endPopup();
	}
}
