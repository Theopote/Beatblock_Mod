package com.beatblock.timeline.rendering;

import imgui.ImGui;

/** 建造图层拖放悬停时的高亮边框。 */
public final class TimelineBuildLayerDropHighlight {

	private static final int DROP_HIGHLIGHT_COLOR = 0x88_66_CC_88;
	private static final int DROP_HIGHLIGHT_FILL = 0x3366CC88;

	private TimelineBuildLayerDropHighlight() {
	}

	public static void drawIfActive(TimelineLayout layout, int rowIndex) {
		if (layout == null || rowIndex < 0) {
			return;
		}
		float y0 = layout.getRowScreenY(rowIndex);
		if (y0 < 0f) {
			return;
		}
		float y1 = y0 + layout.getRowHeight(rowIndex);
		float x0 = layout.contentLeft;
		float x1 = layout.contentLeft + layout.contentWidth;
		ImGui.getWindowDrawList().addRectFilled(x0, y0, x1, y1, DROP_HIGHLIGHT_FILL);
		ImGui.getWindowDrawList().addRect(x0, y0, x1, y1, DROP_HIGHLIGHT_COLOR, 3f, 0, 2f);
	}
}
