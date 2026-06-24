package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.client.imgui.ImGuiRenderer;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/** 拖放区 UI。 */
final class AudioAnalysisDropZoneControls {

	private AudioAnalysisDropZoneControls() {
	}

	static void renderDropZone(AudioAnalysisPanelHost host) {
		AudioAnalysisPanelUiState state = host.uiState();
		state.prunePanelHint();
		float availX = ImGui.getContentRegionAvailX();
		boolean hasHint = state.hasPanelHint();
		float zoneH = hasHint ? 76f : 56f;

		ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.12f, 0.11f, 0.18f, 1f);
		ImGui.pushStyleColor(ImGuiCol.Border, 0.40f, 0.38f, 0.60f, 0.45f);
		ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6f);
		ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1f);

		ImGui.beginChild("##DropZone", availX, zoneH, true);
		ImGui.popStyleVar(2);
		ImGui.popStyleColor(2);

		if (ImGui.isWindowHovered()) {
			float x0 = ImGui.getWindowPosX();
			float y0 = ImGui.getWindowPosY();
			float x1 = x0 + ImGui.getWindowWidth();
			float y1 = y0 + ImGui.getWindowHeight();
			ImGui.getWindowDrawList().addRectFilled(x0, y0, x1, y1, 0x1F9A90E8, 6f);
			ImGui.getWindowDrawList().addRect(x0, y0, x1, y1, 0xCCB9B0FF, 6f, 0, 1.5f);
		}

		float textH = ImGui.getTextLineHeightWithSpacing() * 2f;
		ImGui.setCursorPosY(Math.max(6f, (zoneH - textH) * 0.5f - (hasHint ? 4f : 0f)));

		AudioAnalysisPanelImGui.centerText("拖入音频文件 / 点击 + 选择");
		AudioAnalysisPanelImGui.centerText("MP3 · WAV · OGG · FLAC");

		if (hasHint) {
			ImGui.spacing();
			if (state.panelHintError()) {
				ImGui.pushStyleColor(ImGuiCol.Text, 0.92f, 0.36f, 0.36f, 1f);
				AudioAnalysisPanelImGui.centerText(state.panelHintText());
				ImGui.popStyleColor();
			} else {
				AudioAnalysisPanelImGui.centerText(state.panelHintText());
			}
		}

		if (ImGui.beginDragDropTarget()) {
			byte[] raw = ImGui.acceptDragDropPayload("BB_OS_FILE_PATH");
			if (raw != null) {
				String filePath = new String(raw).trim();
				host.handleIncomingAudioPath(filePath);
			}
			ImGui.endDragDropTarget();
		}

		String osDropped;
		while ((osDropped = ImGuiRenderer.getInstance().pollDroppedFilePath()) != null) {
			host.handleIncomingAudioPath(osDropped);
		}

		ImGui.endChild();
	}
}
