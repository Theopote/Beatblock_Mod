package com.beatblock.timeline.rendering;

import imgui.ImGui;

/** 时间线轨道区 hover 高亮：仅高亮鼠标所在行。 */
public final class TimelineRowHoverHighlighter {

	private static final int ROW_HOVER_FILL = 0x18_88_99_AA;
	private static final int ROW_HOVER_BORDER = 0x44_88_99_AA;

	private TimelineRowHoverHighlighter() {
	}

	public static void drawRowHoverHighlight(TimelineLayout layout) {
		if (layout == null || !isPointerInTrackArea(layout)) return;

		int hoveredRow = layout.findRowAtScreenY(ImGui.getMousePosY());
		if (hoveredRow < 0 || !layout.isRowVisible(hoveredRow)) return;

		float x0 = layout.trackHeaderLeft;
		float x1 = layout.contentLeft + layout.contentWidth;
		float y0 = layout.getRowScreenY(hoveredRow);
		float y1 = y0 + layout.getRowHeight(hoveredRow);
		if (y0 < 0 || y1 <= y0) return;

		var dl = ImGui.getWindowDrawList();
		dl.addRectFilled(x0, y0, x1, y1, ROW_HOVER_FILL, 2f);
		dl.addRect(x0, y0, x1, y1, ROW_HOVER_BORDER, 2f, 0, 0.75f);
	}

	private static boolean isPointerInTrackArea(TimelineLayout layout) {
		if (!ImGui.isWindowHovered()) return false;
		float mx = ImGui.getMousePosX();
		float my = ImGui.getMousePosY();
		float x0 = layout.trackHeaderLeft;
		float x1 = layout.contentLeft + layout.contentWidth;
		return mx >= x0 && mx <= x1
			&& my >= layout.contentTop
			&& my <= layout.contentTop + layout.contentHeight;
	}
}
