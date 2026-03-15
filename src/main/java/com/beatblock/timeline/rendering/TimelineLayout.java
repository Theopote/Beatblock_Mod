package com.beatblock.timeline.rendering;

/**
 * 时间线 UI 布局参数：由面板或编辑器在每帧根据窗口计算，供渲染与交互使用。
 */
public final class TimelineLayout {

	public float contentLeft;       // 内容区左侧（屏幕）
	public float startY;            // 时间线区域顶部 Y
	public float timelineWidth;     // 内容区宽度
	public float rulerHeight = 20f;
	public float rowHeight = 22f;
	public float trackLabelWidth = 110f;

	/** 可交互轨道行在内容区的 Y 偏移（相对 startY + rulerHeight），与 INTERACTIVE_TRACK_IDS 对应 */
	public float[] interactiveRowOffsets = new float[0];

	public float baseContentScreenY() {
		return startY + rulerHeight;
	}
}
