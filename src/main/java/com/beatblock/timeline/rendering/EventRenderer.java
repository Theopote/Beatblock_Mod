package com.beatblock.timeline.rendering;

import com.beatblock.timeline.*;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;

import java.util.List;

/**
 * 绘制时间线事件：动画块、关键帧、全局事件、频段点。
 */
public final class EventRenderer {

	private static final int EVENT_DOT_COLOR = 0xFF_AA_CC_FF;
	private static final int KEYFRAME_COLOR = 0xFF_FF_CC_66;
	private static final int GLOBAL_EVENT_COLOR = 0xFF_AA_FF_AA;
	private static final int SELECTED_BORDER_COLOR = 0xFF_FF_FF_00;

	public void renderFrequencyDots(float rowY, List<FrequencyEvent> events, TimelineLayout layout, TimelineViewState view) {
		if (view == null || layout == null) return;
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(layout.trackLabelWidth);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		for (FrequencyEvent e : events) {
			double t = e.getTimeSeconds();
			if (t < vs || t > ve) continue;
			float x = view.timeToScreen(t);
			if (x >= -4 && x <= layout.timelineWidth + 4) {
				float r = 3f + e.getEnergy() * 3f;
				ImGui.getWindowDrawList().addCircleFilled(baseX + x, baseY, r, EVENT_DOT_COLOR);
			}
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}

	public void renderAnimationEventBlocks(float rowY, List<TimelineAnimationEvent> events, TimelineLayout layout, TimelineViewState view, SelectionState selection) {
		if (view == null || layout == null) return;
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(layout.trackLabelWidth);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		for (TimelineAnimationEvent e : events) {
			double t = e.getTimeSeconds();
			double end = e.getEndTimeSeconds();
			if (end < vs || t > ve) continue;
			float x = view.timeToScreen(t);
			float w = (float) (e.getDurationSeconds() * view.getZoom());
			w = Math.max(8f, Math.min(w, layout.timelineWidth - x + 1));
			if (x + w >= -2 && x <= layout.timelineWidth + 2) {
				float y0 = baseY - layout.rowHeight * 0.35f;
				float y1 = baseY + layout.rowHeight * 0.35f;
				ImGui.getWindowDrawList().addRectFilled(baseX + x, y0, baseX + x + w, y1, KEYFRAME_COLOR, 2f);
				if (selection != null && selection.isEventSelected(e.getEventId())) {
					ImGui.getWindowDrawList().addRect(baseX + x, y0, baseX + x + w, y1, SELECTED_BORDER_COLOR, 0f, 0, 2f);
				}
			}
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}

	public void renderCameraKeyframeRow(float rowY, List<CameraKeyframe> keyframes, TimelineLayout layout, TimelineViewState view) {
		if (view == null || layout == null) return;
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(layout.trackLabelWidth);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		for (CameraKeyframe k : keyframes) {
			double t = k.getTimeSeconds();
			if (t < vs || t > ve) continue;
			float x = view.timeToScreen(t);
			if (x >= -8 && x <= layout.timelineWidth + 8) {
				ImGui.getWindowDrawList().addTriangleFilled(
					baseX + x, baseY - 6,
					baseX + x - 5, baseY + 5,
					baseX + x + 5, baseY + 5,
					KEYFRAME_COLOR
				);
			}
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}

	public void renderGlobalEventRow(float rowY, List<GlobalEvent> events, TimelineLayout layout, TimelineViewState view) {
		if (view == null || layout == null) return;
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(layout.trackLabelWidth);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		for (GlobalEvent e : events) {
			double t = e.getTimeSeconds();
			if (t < vs || t > ve) continue;
			float x = view.timeToScreen(t);
			if (x >= -6 && x <= layout.timelineWidth + 6) {
				ImGui.getWindowDrawList().addCircleFilled(baseX + x, baseY, 5f, GLOBAL_EVENT_COLOR);
			}
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}
}
