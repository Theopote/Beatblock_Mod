package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/** 音频解析面板 ImGui 渲染入口（供 {@code AudioAnalysisPanel} 调用）。 */
public final class AudioAnalysisPanelRenderer {

	private AudioAnalysisPanelRenderer() {
	}

	public static float outerPaddingX() {
		return AudioAnalysisPanelImGui.PANEL_OUTER_PADDING_X;
	}

	public static float outerPaddingY() {
		return AudioAnalysisPanelImGui.PANEL_OUTER_PADDING_Y;
	}

	public static void renderContent(AudioAnalysisPanelHost host) {
		AudioAnalysisPanelUiState uiState = host.uiState();
		AudioAnalysisToolbarControls.renderToolbar(host);
		AudioAnalysisRuntimeControls.renderPythonRuntimeHint(host);

		ImGui.setCursorPosX(ImGui.getCursorStartPosX());

		List<AudioAsset> assets = AudioAssetManager.getInstance().getAssets();

		float totalW = Math.max(0f, ImGui.getContentRegionAvailX());
		float totalH = ImGui.getContentRegionAvailY() - AudioAnalysisPanelImGui.FOOTER_RESERVED_HEIGHT;
		float splitterW = uiState.detailExpanded() ? AudioAnalysisPanelImGui.PANEL_GAP : 0f;

		float detailW = 0f;
		float listW = totalW;
		if (uiState.detailExpanded()) {
			float minRatio = AudioAnalysisPanelImGui.MIN_DETAIL_PANEL_WIDTH / Math.max(1f, totalW);
			float maxRatio = (totalW - AudioAnalysisPanelImGui.MIN_LIST_PANEL_WIDTH - splitterW) / Math.max(1f, totalW);
			if (maxRatio < minRatio) {
				minRatio = 0.5f;
				maxRatio = 0.5f;
			}
			uiState.setDetailRatio(AudioAnalysisPanelImGui.clamp(uiState.detailRatio(), minRatio, maxRatio));
			detailW = totalW * uiState.detailRatio();
			listW = totalW - detailW - splitterW;
		}

		ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,
			AudioAnalysisPanelImGui.LIST_PANEL_PADDING,
			AudioAnalysisPanelImGui.LIST_PANEL_PADDING);
		ImGui.beginChild("##AudioList", listW, totalH, false, ImGuiWindowFlags.NoScrollbar);
		ImGui.popStyleVar();
		AudioAnalysisDropZoneControls.renderDropZone(host);
		ImGui.spacing();
		AudioAnalysisAssetListControls.renderAssetList(host, assets);
		ImGui.endChild();

		ImGui.sameLine(0f, 0f);
		if (uiState.detailExpanded()) {
			ImGui.invisibleButton("##detail_splitter", splitterW, totalH);
			if (ImGui.isItemHovered() || ImGui.isItemActive()) {
				ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("左右拖动调整比例");
			}
			if (ImGui.isItemActive()) {
				float deltaRatio = ImGui.getIO().getMouseDeltaX() / Math.max(1f, totalW);
				float minRatio = AudioAnalysisPanelImGui.MIN_DETAIL_PANEL_WIDTH / Math.max(1f, totalW);
				float maxRatio = (totalW - AudioAnalysisPanelImGui.MIN_LIST_PANEL_WIDTH - splitterW) / Math.max(1f, totalW);
				uiState.setDetailRatio(AudioAnalysisPanelImGui.clamp(
					uiState.detailRatio() - deltaRatio, minRatio, maxRatio));
				float listPercent = (1f - uiState.detailRatio()) * 100f;
				float detailPercent = uiState.detailRatio() * 100f;
				ImGui.setTooltip(String.format("%.0f : %.0f", listPercent, detailPercent));
			}

			ImGui.sameLine(0f, 0f);
			ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 4f);
			ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,
				AudioAnalysisPanelImGui.DETAIL_PANEL_PADDING,
				AudioAnalysisPanelImGui.DETAIL_PANEL_PADDING);
			ImGui.beginChild("##AudioDetail", detailW, totalH, true, ImGuiWindowFlags.NoScrollbar);
			ImGui.popStyleVar(2);
			AudioAnalysisAssetDetailControls.renderDetailPanel(host, uiState.selectedAsset());
			ImGui.endChild();
		} else {
			IconButtonStyle.pushBeatBlockIconButton();
			if (ImGui.button(Icons.Layout.RIGHT_EXPAND + "##expand",
				AudioAnalysisPanelImGui.ICON_BTN, AudioAnalysisPanelImGui.ICON_BTN)) {
				uiState.setDetailExpanded(true);
			}
			IconButtonStyle.popBeatBlockIconButton();
			if (ImGui.isItemHovered()) ImGui.setTooltip("展开详情面板");
		}

		ImGui.separator();
		AudioAnalysisAssetListControls.renderFooter(host, assets);
	}

	public static String chooseFilePath(AudioAnalysisPanelHost host) {
		return AudioAnalysisFilePicker.choose(host.uiState().importPath(),
			msg -> host.uiState().setPanelHint(msg, true));
	}
}
