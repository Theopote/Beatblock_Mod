package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.WaveformData;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;

/**
 * 绘制音频波形轨：█████░░░████
 */
public final class WaveformRenderer {

	private static final int WAVEFORM_COLOR = 0xFF_66_AA_FF;
	private static final double EPS = 1e-6;

	private WaveformData cachedWaveform;
	private int cachedWaveformSampleCount = -1;
	private double cachedDuration = Double.NaN;
	private double cachedViewStart = Double.NaN;
	private double cachedViewEnd = Double.NaN;
	private float cachedZoom = Float.NaN;
	private float cachedTimelineWidth = Float.NaN;
	private int cachedRenderSamples = -1;
	private float[] cachedXs = new float[0];
	private float[] cachedSamples = new float[0];
	private int cachedCount;

	private static boolean nearlyEqual(double a, double b) {
		return Math.abs(a - b) <= EPS;
	}

	private void ensureCache(Timeline timeline, TimelineLayout layout, TimelineViewState view, WaveformData wf,
			double viewStart, double viewEnd) {
		double dur = timeline.getDurationSeconds();
		int renderSamples = (int) Math.min(layout.timelineWidth, 800);
		boolean cacheValid = cachedWaveform == wf
				&& cachedWaveformSampleCount == wf.getSampleCount()
				&& nearlyEqual(cachedDuration, dur)
				&& nearlyEqual(cachedViewStart, viewStart)
				&& nearlyEqual(cachedViewEnd, viewEnd)
				&& Math.abs(cachedZoom - view.getZoom()) <= EPS
				&& Math.abs(cachedTimelineWidth - layout.timelineWidth) <= EPS
				&& cachedRenderSamples == renderSamples;
		if (cacheValid) return;

		if (cachedXs.length < renderSamples) {
			cachedXs = new float[renderSamples];
			cachedSamples = new float[renderSamples];
		}
		cachedCount = 0;
		for (int i = 0; i < renderSamples; i++) {
			double t = viewStart + (viewEnd - viewStart) * (double) i / renderSamples;
			if (t < 0 || t > dur) continue;
			int idx = wf.timeToIndex(t);
			cachedXs[cachedCount] = view.timeToScreen(t);
			cachedSamples[cachedCount] = wf.getSample(idx);
			cachedCount++;
		}

		cachedWaveform = wf;
		cachedWaveformSampleCount = wf.getSampleCount();
		cachedDuration = dur;
		cachedViewStart = viewStart;
		cachedViewEnd = viewEnd;
		cachedZoom = view.getZoom();
		cachedTimelineWidth = layout.timelineWidth;
		cachedRenderSamples = renderSamples;
	}

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
			ensureCache(timeline, layout, view, wf, viewStart, viewEnd);
			float halfH = layout.rowHeight * 0.4f;
			for (int i = 0; i < cachedCount; i++) {
				float x = cachedXs[i];
				float s = cachedSamples[i];
				if (x < -1 || x > layout.timelineWidth + 1) continue;
				float y0 = minY + layout.rowHeight * 0.5f;
				float y1 = y0 - s * halfH;
				ImGui.getWindowDrawList().addLine(minX + x, y0, minX + x, y1, WAVEFORM_COLOR, 1f);
			}
		} else {
			cachedWaveform = null;
			cachedWaveformSampleCount = -1;
			cachedCount = 0;
			ImGui.textDisabled("~~~~ 波形（导入音乐后生成）~~~~");
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}
}
