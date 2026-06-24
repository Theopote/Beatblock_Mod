package com.beatblock.ui.panels.audioanalysis;

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
		if (ImGui.isItemHovered()) ImGui.setTooltip(state.detailExpanded() ? "折叠详情" : "展开详情");

		if (host.presenter().isAnalyzerAvailable()) {
			ImGui.sameLine();
			ImGui.spacing();
			ImGui.sameLine();
			state.demucsToggle().set(host.presenter().isUseDemucs());
			if (ImGui.checkbox("新任务默认 Demucs##demucsToggle", state.demucsToggle())) {
				host.presenter().setUseDemucs(state.demucsToggle().get());
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("只影响之后新加入或重新提交的任务\n已在队列中的任务会保留提交时锁定的模式\n关闭后使用仅 librosa 的 Basic 快速分析模式");
			}
		}

		renderAddPopup(host);

		ImGui.separator();
	}

	static void renderAddPopup(AudioAnalysisPanelHost host) {
		AudioAnalysisPanelUiState state = host.uiState();
		ImGui.setNextWindowSize(460f, 0f, ImGuiCond.Always);
		if (!ImGui.beginPopup("##AddAudioPopup")) return;

		ImGui.text("选择音频文件");
		if (ImGui.button("浏览文件...##browseAudio", 120f, 0f)) {
			String chosenPath = host.chooseAudioFilePath();
			if (chosenPath != null && !chosenPath.isBlank()) {
				state.importPath().set(chosenPath);
				host.handleIncomingAudioPath(chosenPath);
				ImGui.closeCurrentPopup();
			}
		}

		ImGui.spacing();
		if (state.importPath().get().isBlank()) {
			ImGui.textDisabled("尚未选择文件");
		} else {
			AudioAnalysisPanelImGui.renderCollapsedInlineValue(state, state.importPath().get(), "##importPathPreview", null);
		}

		ImGui.spacing();
		ImGui.textDisabled("支持 MP3 · WAV · OGG · FLAC");
		ImGui.textDisabled("提示：选择文件后会自动开始解析");
		ImGui.spacing();

		boolean add = ImGui.button("手动添加并解析##add", 150f, 0f);
		ImGui.sameLine();
		boolean cancel = ImGui.button("取消##cancel");

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
