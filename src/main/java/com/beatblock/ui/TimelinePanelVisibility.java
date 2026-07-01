package com.beatblock.ui;

import imgui.type.ImBoolean;

/**
 * 时间线交互层请求打开属性面板（由 {@link BeatBlockUIManager} 注册）。
 */
public final class TimelinePanelVisibility {

	private static ImBoolean timelineProperties;

	private TimelinePanelVisibility() {
	}

	public static void bind(BeatBlockPanelVisibility visibility) {
		timelineProperties = visibility != null ? visibility.timelineProperties : null;
	}

	public static void openTimelineProperties() {
		if (timelineProperties != null) {
			timelineProperties.set(true);
		}
	}
}
