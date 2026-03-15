package com.beatblock.timeline.rendering;

import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * 绘制轨道标签（左侧轨道名）。
 */
public final class TrackRenderer {

	public float drawTrackLabel(float rowY, String label, boolean isGroup) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(4);
		if (isGroup) {
			ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.85f, 0.7f, 1f);
		}
		ImGui.text(label);
		if (isGroup) {
			ImGui.popStyleColor();
		}
		return isGroup ? rowY + 22f : rowY;
	}
}
