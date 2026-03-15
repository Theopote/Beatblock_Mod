package com.beatblock.timeline.rendering;

import com.beatblock.timeline.FrequencyBand;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.SelectionBox;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;

/**
 * 时间线渲染入口：按顺序绘制标尺、网格、轨道标签、波形、事件、播放头、框选。
 */
public final class TimelineRenderer {

	private static final float TRACK_LABEL_WIDTH = 110f;
	private static final float ROW_HEIGHT = 22f;
	private static final float RULER_HEIGHT = 20f;
	private static final int PLAYHEAD_COLOR = 0xFF_FF_66_66;
	private static final int SELECTED_BORDER_COLOR = 0xFF_FF_FF_00;

	private final GridRenderer gridRenderer = new GridRenderer();
	private final TrackRenderer trackRenderer = new TrackRenderer();
	private final EventRenderer eventRenderer = new EventRenderer();
	private final WaveformRenderer waveformRenderer = new WaveformRenderer();

	/** 可交互轨道 ID，与下面 rowOffsets 对应 */
	private static final String[] INTERACTIVE_TRACK_IDS = {
		Timeline.TRACK_ID_ANIMATION_BLOCK,
		Timeline.TRACK_ID_ANIMATION_AUTO,
		Timeline.TRACK_ID_CAMERA,
		Timeline.TRACK_ID_GLOBAL
	};

	public void render(
		Timeline timeline,
		TimelineViewState viewState,
		SelectionState selectionState,
		TimelineClock clock,
		SelectionBox selectionBox,
		float contentWidth
	) {
		if (timeline == null || viewState == null) return;

		TimelineLayout layout = new TimelineLayout();
		layout.trackLabelWidth = TRACK_LABEL_WIDTH;
		layout.rowHeight = ROW_HEIGHT;
		layout.rulerHeight = RULER_HEIGHT;
		layout.timelineWidth = Math.max(200f, contentWidth - TRACK_LABEL_WIDTH - 20f);
		layout.startY = ImGui.getCursorPosY();
		layout.contentLeft = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;

		float rowY = layout.startY + RULER_HEIGHT;

		// 标尺
		gridRenderer.renderRuler(layout.startY, viewState, layout);
		// 网格（内容区高度约 12 行）
		gridRenderer.render(viewState, layout, 12 * ROW_HEIGHT);

		// 轨道标签 + 内容
		rowY = trackRenderer.drawTrackLabel(rowY, "音频", true);
		rowY = trackRenderer.drawTrackLabel(rowY, "波形", false);
		waveformRenderer.render(rowY, timeline, layout, viewState);
		rowY += ROW_HEIGHT;

		rowY = trackRenderer.drawTrackLabel(rowY, "低频", false);
		eventRenderer.renderFrequencyDots(rowY, timeline.getFrequencyEventsByBand(FrequencyBand.LOW), layout, viewState);
		rowY += ROW_HEIGHT;
		rowY = trackRenderer.drawTrackLabel(rowY, "中频", false);
		eventRenderer.renderFrequencyDots(rowY, timeline.getFrequencyEventsByBand(FrequencyBand.MID), layout, viewState);
		rowY += ROW_HEIGHT;
		rowY = trackRenderer.drawTrackLabel(rowY, "高频", false);
		eventRenderer.renderFrequencyDots(rowY, timeline.getFrequencyEventsByBand(FrequencyBand.HIGH), layout, viewState);
		rowY += ROW_HEIGHT;

		rowY = trackRenderer.drawTrackLabel(rowY, "动画", true);
		rowY = trackRenderer.drawTrackLabel(rowY, "方块动画", false);
		eventRenderer.renderAnimationEventBlocks(rowY, timeline.getBlockAnimationEvents(), layout, viewState, selectionState);
		rowY += ROW_HEIGHT;
		rowY = trackRenderer.drawTrackLabel(rowY, "自动动画", false);
		eventRenderer.renderAnimationEventBlocks(rowY, timeline.getAutoAnimationEvents(), layout, viewState, selectionState);
		rowY += ROW_HEIGHT;

		rowY = trackRenderer.drawTrackLabel(rowY, "摄像机", false);
		rowY = trackRenderer.drawTrackLabel(rowY, "关键帧", false);
		eventRenderer.renderCameraKeyframeRow(rowY, timeline.getCameraKeyframes(), layout, viewState);
		rowY += ROW_HEIGHT;

		rowY = trackRenderer.drawTrackLabel(rowY, "全局事件", false);
		rowY = trackRenderer.drawTrackLabel(rowY, "事件", false);
		eventRenderer.renderGlobalEventRow(rowY, timeline.getGlobalEvents(), layout, viewState);
		rowY += ROW_HEIGHT;

		// 播放头
		if (clock != null) {
			double currentTime = clock.getCurrentTimeSeconds();
			double viewStart = viewState.getViewStartTimeSeconds();
			float playheadX = viewState.timeToScreen(currentTime);
			if (playheadX >= -2 && playheadX <= layout.timelineWidth + 2) {
				float padX = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
				float py0 = ImGui.getWindowPosY() + layout.startY + ImGui.getScrollY();
				float py1 = ImGui.getWindowPosY() + rowY + ImGui.getScrollY();
				ImGui.getWindowDrawList().addLine(padX + playheadX, py0, padX + playheadX, py1, PLAYHEAD_COLOR, 2f);
			}
		}

		// 框选矩形
		if (selectionBox != null && selectionBox.isActive()) {
			ImGui.getWindowDrawList().addRect(selectionBox.getMinX(), selectionBox.getMinY(), selectionBox.getMaxX(), selectionBox.getMaxY(), SELECTED_BORDER_COLOR, 0f, 0, 1.5f);
		}
	}
}
