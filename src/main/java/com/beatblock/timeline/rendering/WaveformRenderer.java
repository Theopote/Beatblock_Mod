package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.WaveformData;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.util.TimeUtils;
import imgui.ImGui;

/**
 * 绘制音频波形轨：█████░░░████
 */
public final class WaveformRenderer {

	private static final int WAVEFORM_COLOR = 0xFF_66_AA_FF;

	public void render(float rowY, Timeline timeline, TimelineLayout layout, TimelineViewState view) {
		if (timeline == null || layout == null || view == null) return;
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(layout.trackLabelWidth);
		float minX = ImGui.getCursorScreenPosX();
		float minY = ImGui.getCursorScreenPosY();
		WaveformData wf = timeline.getWaveform();
		double viewStart = view.getViewStartTimeSeconds();
		double viewEnd = view.getViewEndTimeSeconds();
		if (wf != null && wf.getSampleCount() > 0) {
			double dur = timeline.getDurationSeconds();
			int samples = (int) Math.min(layout.timelineWidth, 800);
			float halfH = layout.rowHeight * 0.4f;
			for (int i = 0; i < samples; i++) {
				double t = viewStart + (viewEnd - viewStart) * (double) i / samples;
				if (t < 0 || t > dur) continue;
				int idx = wf.timeToIndex(t);
				float s = wf.getSample(idx);
				float x = TimeUtils.timeToScreen(t, viewStart, view.getZoom());
				if (x < -1 || x > layout.timelineWidth + 1) continue;
				float y0 = minY + layout.rowHeight * 0.5f;
				float y1 = y0 - s * halfH;
				ImGui.getWindowDrawList().addLine(minX + x, y0, minX + x, y1, WAVEFORM_COLOR, 1f);
			}
		} else {
			ImGui.textDisabled("~~~~ 波形（导入音乐后生成）~~~~");
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}
}
