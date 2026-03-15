package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.timeline.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/**
 * 底部通栏时间线面板：分层轨道系统。
 * Audio（Waveform / Low / Mid / High）| Animation（Block / Auto）| Camera | Global Event。
 * 显示播放头、波形占位与频段/事件点。
 */
public class TimelinePanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final float TRACK_LABEL_WIDTH = 110f;
	private static final float ROW_HEIGHT = 22f;
	private static final float RULER_HEIGHT = 20f;
	private static final float PLAYHEAD_COLOR = 0xFF_FF_66_66;
	private static final float ZERO_COLOR = 0xFF_88_88_88;
	private static final float WAVEFORM_COLOR = 0xFF_66_AA_FF;
	private static final float EVENT_DOT_COLOR = 0xFF_AA_CC_FF;
	private static final float KEYFRAME_COLOR = 0xFF_FF_CC_66;
	private static final float GLOBAL_EVENT_COLOR = 0xFF_AA_FF_AA;

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}

		double duration = getDuration();
		double currentTime = getCurrentTime();
		TimelineModel model = BeatBlock.timelineModel;
		if (model == null) {
			ImGui.text("时间线（未加载模型）");
			ImGui.end();
			return;
		}

		// 顶部：标题 + 时间信息
		ImGui.text("时间线");
		ImGui.sameLine();
		ImGui.textDisabled("(音乐 | 摄像机 | 动画事件)");
		ImGui.sameLine(ImGui.getWindowWidth() - 120);
		ImGui.text(String.format("%.1fs / %.1fs", currentTime, duration));
		ImGui.separator();

		float contentWidth = ImGui.getContentRegionAvailX();
		float startY = ImGui.getCursorPosY();
		float timelineWidth = Math.max(200f, contentWidth - TRACK_LABEL_WIDTH - 20f);
		float pixelsPerSecond = duration > 0 ? (timelineWidth / (float) duration) : 10f;

		// 时间标尺行（左侧留空，右侧画刻度）
		drawTrackLabel(startY, "", false);
		drawRuler(startY, timelineWidth, duration, pixelsPerSecond);
		float rowY = startY + RULER_HEIGHT;

		// 轨道区：左侧标签 + 右侧内容
		// --- Audio Track ---
		rowY = drawTrackLabel(rowY, "音频", true);
		drawTrackLabel(rowY, "波形", false);
		rowY = drawAudioWaveformRow(rowY, model, timelineWidth, pixelsPerSecond);
		drawTrackLabel(rowY, "低频", false);
		rowY = drawFrequencyDots(rowY, model.getFrequencyEventsByBand(FrequencyBand.LOW), timelineWidth, pixelsPerSecond);
		drawTrackLabel(rowY, "中频", false);
		rowY = drawFrequencyDots(rowY, model.getFrequencyEventsByBand(FrequencyBand.MID), timelineWidth, pixelsPerSecond);
		drawTrackLabel(rowY, "高频", false);
		rowY = drawFrequencyDots(rowY, model.getFrequencyEventsByBand(FrequencyBand.HIGH), timelineWidth, pixelsPerSecond);

		// --- Animation Track ---
		rowY = drawTrackLabel(rowY, "动画", true);
		drawTrackLabel(rowY, "方块动画", false);
		rowY = drawAnimationEventBlocks(rowY, model.getBlockAnimationEvents(), timelineWidth, pixelsPerSecond);
		drawTrackLabel(rowY, "自动动画", false);
		rowY = drawAnimationEventBlocks(rowY, model.getAutoAnimationEvents(), timelineWidth, pixelsPerSecond);

		// --- Camera Track ---
		rowY = drawTrackLabel(rowY, "摄像机", false);
		drawTrackLabel(rowY, "关键帧", false);
		rowY = drawCameraKeyframeRow(rowY, model.getCameraKeyframes(), timelineWidth, pixelsPerSecond);

		// --- Global Event Track ---
		rowY = drawTrackLabel(rowY, "全局事件", false);
		drawTrackLabel(rowY, "事件", false);
		rowY = drawGlobalEventRow(rowY, model.getGlobalEvents(), timelineWidth, pixelsPerSecond);

		// 播放头（贯穿轨道区域）
		float playheadX = (float) (currentTime * pixelsPerSecond);
		float padX = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
		float py0 = ImGui.getWindowPosY() + startY + ImGui.getScrollY();
		float py1 = ImGui.getWindowPosY() + rowY + ImGui.getScrollY();
		ImGui.getWindowDrawList().addLine(padX + playheadX, py0, padX + playheadX, py1, (int) PLAYHEAD_COLOR, 2f);

		ImGui.end();
	}

	private double getDuration() {
		if (BeatBlock.timelineModel != null && BeatBlock.timelineModel.getDurationSeconds() > 0) {
			return BeatBlock.timelineModel.getDurationSeconds();
		}
		if (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.getDurationSeconds() > 0) {
			return BeatBlock.musicPlayer.getDurationSeconds();
		}
		return 60.0;
	}

	private double getCurrentTime() {
		if (BeatBlock.musicPlayer != null) {
			return BeatBlock.musicPlayer.getCurrentTimeSeconds();
		}
		return 0;
	}

	private void drawRuler(float startY, float width, double duration, float pixelsPerSecond) {
		float padX = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
		float screenY = ImGui.getWindowPosY() + startY + ImGui.getScrollY();
		int gray = (int) ZERO_COLOR;
		double step = duration > 30 ? 5 : (duration > 10 ? 2 : 1);
		for (double t = 0; t <= duration; t += step) {
			float x = (float) (t * pixelsPerSecond);
			ImGui.getWindowDrawList().addLine(padX + x, screenY, padX + x, screenY + RULER_HEIGHT, gray, 1f);
			ImGui.getWindowDrawList().addText(padX + x + 2, screenY + 2, gray, String.format("%.0f", t));
		}
	}

	/** 绘制轨道标签。isGroup=true 时占一整行并推进 rowY；否则只画标签不推进（与右侧内容同行）。 */
	private float drawTrackLabel(float rowY, String label, boolean isGroup) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(4);
		if (isGroup) {
			ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.85f, 0.7f, 1f);
		}
		ImGui.text(label);
		if (isGroup) {
			ImGui.popStyleColor();
		}
		return isGroup ? rowY + ROW_HEIGHT : rowY;
	}

	private float drawAudioWaveformRow(float rowY, TimelineModel model, float width, float pixelsPerSecond) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float minX = ImGui.getCursorScreenPosX();
		float minY = ImGui.getCursorScreenPosY();
		WaveformData wf = model.getWaveform();
		if (wf != null && wf.getSampleCount() > 0) {
			int n = wf.getSampleCount();
			float halfH = ROW_HEIGHT * 0.4f;
			for (int i = 0; i < width && i < n; i++) {
				double t = (double) i / width * model.getDurationSeconds();
				int idx = wf.timeToIndex(t);
				float s = wf.getSample(idx);
				float y0 = minY + ROW_HEIGHT * 0.5f;
				float y1 = y0 - s * halfH;
				ImGui.getWindowDrawList().addLine(minX + i, y0, minX + i, y1, (int) WAVEFORM_COLOR, 1f);
			}
		} else {
			ImGui.textDisabled("~~~~ 波形（导入音乐后生成）~~~~");
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawFrequencyDots(float rowY, List<FrequencyEvent> events, float width, float pixelsPerSecond) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (FrequencyEvent e : events) {
			float x = (float) (e.getTimeSeconds() * pixelsPerSecond);
			if (x >= 0 && x <= width) {
				float r = 3f + e.getEnergy() * 3f;
				ImGui.getWindowDrawList().addCircleFilled(baseX + x, baseY, r, (int) EVENT_DOT_COLOR);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawAnimationEventBlocks(float rowY, List<TimelineAnimationEvent> events, float width, float pixelsPerSecond) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (TimelineAnimationEvent e : events) {
			float x = (float) (e.getTimeSeconds() * pixelsPerSecond);
			float w = (float) (e.getDurationSeconds() * pixelsPerSecond);
			w = Math.max(8f, Math.min(w, width - x));
			if (x + w >= 0 && x <= width) {
				ImGui.getWindowDrawList().addRectFilled(
					baseX + x, baseY - ROW_HEIGHT * 0.35f,
					baseX + x + w, baseY + ROW_HEIGHT * 0.35f,
					(int) KEYFRAME_COLOR, 2f
				);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawCameraKeyframeRow(float rowY, List<CameraKeyframe> keyframes, float width, float pixelsPerSecond) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (CameraKeyframe k : keyframes) {
			float x = (float) (k.getTimeSeconds() * pixelsPerSecond);
			if (x >= 0 && x <= width) {
				ImGui.getWindowDrawList().addTriangleFilled(
					baseX + x, baseY - 6,
					baseX + x - 5, baseY + 5,
					baseX + x + 5, baseY + 5,
					(int) KEYFRAME_COLOR
				);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawGlobalEventRow(float rowY, List<GlobalEvent> events, float width, float pixelsPerSecond) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (GlobalEvent e : events) {
			float x = (float) (e.getTimeSeconds() * pixelsPerSecond);
			if (x >= 0 && x <= width) {
				ImGui.getWindowDrawList().addCircleFilled(baseX + x, baseY, 5f, (int) GLOBAL_EVENT_COLOR);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}
}
