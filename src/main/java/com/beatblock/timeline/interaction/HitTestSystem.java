package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineLayout;

/**
 * 时间线 HitTest：根据屏幕坐标返回点击到的对象（时间标尺 / 事件 / 轨道等）。
 */
public final class HitTestSystem {

	/**
	 * 时间标尺区域判定。
	 */
	public static HitResult hitTestTimeRuler(float mouseX, float mouseY, float contentLeftX, float rulerTopY, float rulerHeight, float contentWidth, TimelineViewState viewState) {
		return com.beatblock.timeline.editor.TimelineHitTest.hitTestTimeRuler(mouseX, mouseY, contentLeftX, rulerTopY, rulerHeight, contentWidth, viewState);
	}

	/**
	 * 轨道内容区：是否点到某轨道的某 Event 或 Clip。
	 */
	public static HitResult hitTestTrackContent(Timeline timeline, String trackId, float mouseX, float mouseY, float contentLeftX, float rowY, float rowHeight, float contentWidth, TimelineViewState viewState) {
		return com.beatblock.timeline.editor.TimelineHitTest.hitTestTrackContent(timeline, trackId, mouseX, mouseY, contentLeftX, rowY, rowHeight, contentWidth, viewState);
	}
}
