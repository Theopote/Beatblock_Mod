package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.*;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.HitType;
import com.beatblock.timeline.editor.InteractionMode;
import com.beatblock.timeline.editor.TimelineEditor;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineHitTest;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/**
 * 底部通栏时间线面板：接入 TimelineEditor（TimeSystem + Viewport + Selection + Interaction）。
 * Step1：轨道 / 网格 / 事件 / 播放头，时间↔屏幕由 ViewState 驱动。
 */
public class TimelinePanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final float TRACK_LABEL_WIDTH = 110f;
	private static final float ROW_HEIGHT = 22f;
	private static final float RULER_HEIGHT = 20f;
	private static final int PLAYHEAD_COLOR = 0xFF_FF_66_66;
	private static final int ZERO_COLOR = 0xFF_88_88_88;
	private static final int GRID_COLOR = 0x22_88_88_88;
	private static final int WAVEFORM_COLOR = 0xFF_66_AA_FF;
	private static final int EVENT_DOT_COLOR = 0xFF_AA_CC_FF;
	private static final int KEYFRAME_COLOR = 0xFF_FF_CC_66;
	private static final int GLOBAL_EVENT_COLOR = 0xFF_AA_FF_AA;
	private static final int SELECTED_BORDER_COLOR = 0xFF_FF_FF_00;
	private static final float DRAG_THRESHOLD_PX = 4f;

	/** 可交互轨道行（内容区）：动画块、自动动画、摄像机、全局。与下方 rowY 计算对应。 */
	private static final String[] INTERACTIVE_TRACK_IDS = {
		Timeline.TRACK_ID_ANIMATION_BLOCK,
		Timeline.TRACK_ID_ANIMATION_AUTO,
		Timeline.TRACK_ID_CAMERA,
		Timeline.TRACK_ID_GLOBAL
	};

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}

		Timeline model = BeatBlock.timeline;
		TimelineEditor editor = BeatBlock.timelineEditor;
		if (model == null) {
			ImGui.text("时间线（未加载模型）");
			ImGui.end();
			return;
		}

		double duration = getDuration();
		double currentTime = getCurrentTime();
		if (editor != null) {
			editor.syncClockDuration();
			if (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.isPlaying()) {
				editor.getClock().setCurrentTimeSeconds(BeatBlock.musicPlayer.getCurrentTimeSeconds());
			} else {
				editor.getClock().setCurrentTimeSeconds(currentTime);
			}
			currentTime = editor.getClock().getCurrentTimeSeconds();
		}

		ImGui.text("时间线");
		ImGui.sameLine();
		ImGui.textDisabled("(音乐 | 摄像机 | 动画事件)");
		ImGui.sameLine(ImGui.getWindowWidth() - 120);
		ImGui.text(String.format("%.1fs / %.1fs", currentTime, duration));
		ImGui.separator();

		float contentWidth = ImGui.getContentRegionAvailX();
		float startY = ImGui.getCursorPosY();
		float timelineWidth = Math.max(200f, contentWidth - TRACK_LABEL_WIDTH - 20f);

		TimelineViewState viewState = editor != null ? editor.getViewState() : null;
		float zoom = (viewState != null) ? viewState.getZoom() : (duration > 0 ? (timelineWidth / (float) duration) : 10f);
		double viewStart = viewState != null ? viewState.getViewStartTimeSeconds() : 0;
		double viewEnd = viewState != null ? viewState.getViewEndTimeSeconds() : duration;
		// 首次打开（仍为默认 0~60 可见范围）时适配整段时长
		if (viewState != null && duration > 0 && timelineWidth > 0
			&& viewState.getViewEndTimeSeconds() >= 59 && viewState.getViewEndTimeSeconds() <= 61) {
			viewState.fitToDuration(duration, timelineWidth);
			viewStart = viewState.getViewStartTimeSeconds();
			viewEnd = viewState.getViewEndTimeSeconds();
			zoom = viewState.getZoom();
		}

		drawTrackLabel(startY, "", false);
		drawRuler(startY, timelineWidth, viewStart, viewEnd, zoom);
		drawGrid(startY + RULER_HEIGHT, timelineWidth, viewStart, viewEnd, zoom, 260);
		float rowY = startY + RULER_HEIGHT;

		rowY = drawTrackLabel(rowY, "音频", true);
		drawTrackLabel(rowY, "波形", false);
		rowY = drawAudioWaveformRow(rowY, model, timelineWidth, zoom, viewStart, viewEnd);
		drawTrackLabel(rowY, "低频", false);
		rowY = drawFrequencyDots(rowY, model.getFrequencyEventsByBand(FrequencyBand.LOW), timelineWidth, zoom, viewStart, viewEnd);
		drawTrackLabel(rowY, "中频", false);
		rowY = drawFrequencyDots(rowY, model.getFrequencyEventsByBand(FrequencyBand.MID), timelineWidth, zoom, viewStart, viewEnd);
		drawTrackLabel(rowY, "高频", false);
		rowY = drawFrequencyDots(rowY, model.getFrequencyEventsByBand(FrequencyBand.HIGH), timelineWidth, zoom, viewStart, viewEnd);

		rowY = drawTrackLabel(rowY, "动画", true);
		drawTrackLabel(rowY, "方块动画", false);
		rowY = drawAnimationEventBlocks(rowY, model.getBlockAnimationEvents(), timelineWidth, zoom, viewStart, viewEnd, editor != null ? editor.getSelectionState() : null);
		drawTrackLabel(rowY, "自动动画", false);
		rowY = drawAnimationEventBlocks(rowY, model.getAutoAnimationEvents(), timelineWidth, zoom, viewStart, viewEnd, editor != null ? editor.getSelectionState() : null);

		rowY = drawTrackLabel(rowY, "摄像机", false);
		drawTrackLabel(rowY, "关键帧", false);
		rowY = drawCameraKeyframeRow(rowY, model.getCameraKeyframes(), timelineWidth, zoom, viewStart, viewEnd);

		rowY = drawTrackLabel(rowY, "全局事件", false);
		drawTrackLabel(rowY, "事件", false);
		rowY = drawGlobalEventRow(rowY, model.getGlobalEvents(), timelineWidth, zoom, viewStart, viewEnd);

		float playheadX = (float) ((currentTime - viewStart) * zoom);
		if (playheadX >= -2 && playheadX <= timelineWidth + 2) {
			float padX = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
			float py0 = ImGui.getWindowPosY() + startY + ImGui.getScrollY();
			float py1 = ImGui.getWindowPosY() + rowY + ImGui.getScrollY();
			ImGui.getWindowDrawList().addLine(padX + playheadX, py0, padX + playheadX, py1, PLAYHEAD_COLOR, 2f);
		}

		// 框选矩形
		if (editor != null && editor.getSelectionBox().isActive()) {
			var box = editor.getSelectionBox();
			ImGui.getWindowDrawList().addRect(box.getMinX(), box.getMinY(), box.getMaxX(), box.getMaxY(), SELECTED_BORDER_COLOR, 0f, 0, 1.5f);
		}

		// Step2: 鼠标交互（Scrub / 拖拽事件 / 选择）
		if (editor != null && viewState != null) {
			float contentLeft = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
			float scrollY = ImGui.getScrollY();
			float winY = ImGui.getWindowPosY();
			float rulerScreenTop = winY + scrollY + startY;
			float rulerScreenBottom = rulerScreenTop + RULER_HEIGHT;
			float baseContentScreenY = rulerScreenTop + RULER_HEIGHT;
			// 四行可交互轨道在内容区的行偏移（相对 baseContentScreenY）
			float[] rowOffsets = { 5 * ROW_HEIGHT, 6 * ROW_HEIGHT, 8 * ROW_HEIGHT, 10 * ROW_HEIGHT };
			handleTimelineInput(editor, model, duration, contentLeft, rulerScreenTop, rulerScreenBottom,
				baseContentScreenY, rowOffsets, timelineWidth, viewState, viewStart, zoom);
		}

		ImGui.end();
	}

	/**
	 * 处理时间线鼠标：时间标尺 Scrub、轨道内 Event/Clip 拖拽、点击选择。
	 */
	private void handleTimelineInput(TimelineEditor editor, Timeline timeline, double duration,
	                                float contentLeft, float rulerScreenTop, float rulerScreenBottom,
	                                float baseContentScreenY, float[] rowOffsets, float contentWidth,
	                                TimelineViewState viewState, double viewStart, float zoom) {
		if (!ImGui.isWindowHovered()) return;
		float mx = ImGui.getMousePosX();
		float my = ImGui.getMousePosY();
		var ist = editor.getInteractionState();
		var sel = editor.getSelectionState();

		// 鼠标释放：结束当前操作
		if (ImGui.isMouseReleased(0)) {
			if (ist.getMode() == InteractionMode.DRAG_EVENT && ist.getActiveEventId() != null) {
				float dx = mx - ist.getMouseStartX();
				float dy = my - ist.getMouseStartY();
				if (dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) {
					sel.clearEvents();
					sel.selectEvent(ist.getActiveEventId());
				}
			}
			if (ist.getMode() == InteractionMode.BOX_SELECT) {
				// 框选占位：可在此解析框内事件并加入 selection
			}
			ist.setMode(InteractionMode.NONE);
			ist.clearActive();
			editor.getSelectionBox().setActive(false);
			return;
		}

		// 拖拽中
		if (ImGui.isMouseDown(0) && ist.getMode() != InteractionMode.NONE) {
			if (ist.getMode() == InteractionMode.SCRUB_TIME) {
				double t = viewState.screenToTime(mx - contentLeft);
				editor.getClock().seek(Math.max(0, Math.min(t, duration)));
				return;
			}
			if (ist.getMode() == InteractionMode.DRAG_EVENT && ist.getActiveEventId() != null && ist.getActiveTrackId() != null && ist.getActiveClipId() != null) {
				double t = viewState.screenToTime(mx - contentLeft);
				t = Math.max(0, Math.min(t, duration));
				applyEventTime(timeline, ist.getActiveTrackId(), ist.getActiveClipId(), ist.getActiveEventId(), t);
				return;
			}
			return;
		}

		// 鼠标按下：HitTest 并进入模式
		if (ImGui.isMouseClicked(0)) {
			boolean ctrl = ImGui.getIO().getKeyCtrl();
			// 时间标尺 → Scrub
			HitResult rulerHit = TimelineHitTest.hitTestTimeRuler(mx, my, contentLeft, rulerScreenTop, RULER_HEIGHT, contentWidth, viewState);
			if (!rulerHit.isEmpty() && rulerHit.getHitType() == HitType.TIME_HEADER) {
				ist.setMode(InteractionMode.SCRUB_TIME);
				ist.setMouseStart(mx, my);
				editor.getClock().seek(Math.max(0, Math.min(rulerHit.getTimeSeconds(), duration)));
				return;
			}
			// 轨道内容 → Event / Clip
			for (int i = 0; i < INTERACTIVE_TRACK_IDS.length && i < rowOffsets.length; i++) {
				float rowScreenY = baseContentScreenY + rowOffsets[i];
				HitResult hit = TimelineHitTest.hitTestTrackContent(timeline, INTERACTIVE_TRACK_IDS[i], mx, my, contentLeft, rowScreenY, ROW_HEIGHT, contentWidth, viewState);
				if (hit.isEmpty()) continue;
				if (hit.getHitType() == HitType.EVENT || hit.getHitType() == HitType.CLIP) {
					ist.setMode(InteractionMode.DRAG_EVENT);
					ist.setMouseStart(mx, my);
					ist.setActiveEventId(hit.getEventId());
					ist.setActiveClipId(hit.getClipId());
					ist.setActiveTrackId(hit.getTrackId());
					if (!ctrl) sel.clearEvents();
					if (hit.getEventId() != null) sel.selectEvent(hit.getEventId());
					else if (hit.getClipId() != null) sel.selectClip(hit.getClipId());
					return;
				}
			}
			// 点击空白：开始框选，并清空当前选择
			sel.clearEvents();
			sel.clearClips();
			editor.getSelectionBox().setStart(mx, my);
			editor.getSelectionBox().setEnd(mx, my);
			editor.getSelectionBox().setActive(true);
			ist.setMode(InteractionMode.BOX_SELECT);
			ist.setMouseStart(mx, my);
		}
		// 框选拖拽中：更新框终点
		if (ist.getMode() == InteractionMode.BOX_SELECT && ImGui.isMouseDown(0)) {
			editor.getSelectionBox().setEnd(mx, my);
		}
	}

	private void applyEventTime(Timeline timeline, String trackId, String clipId, String eventId, double timeSeconds) {
		Track t = timeline.getTrack(trackId);
		if (t == null) return;
		Clip c = t.getClip(clipId);
		if (c == null) return;
		TimelineEvent e = c.getEvent(eventId);
		if (e != null) e.setTimeSeconds(timeSeconds);
	}

	private double getDuration() {
		if (BeatBlock.timeline != null && BeatBlock.timeline.getDurationSeconds() > 0) {
			return BeatBlock.timeline.getDurationSeconds();
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
		if (BeatBlock.timelineEditor != null) {
			return BeatBlock.timelineEditor.getClock().getCurrentTimeSeconds();
		}
		return 0;
	}

	private float timeToScreen(double timeSeconds, double viewStart, float zoom) {
		return (float) (timeSeconds - viewStart) * zoom;
	}

	private void drawRuler(float startY, float width, double viewStart, double viewEnd, float zoom) {
		float padX = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
		float screenY = ImGui.getWindowPosY() + startY + ImGui.getScrollY();
		double range = Math.max(0.1, viewEnd - viewStart);
		double step = range > 60 ? 10 : (range > 20 ? 5 : (range > 5 ? 2 : (range > 1 ? 1 : 0.5)));
		double t0 = Math.floor(viewStart / step) * step;
		for (double t = t0; t <= viewEnd + 0.001; t += step) {
			float x = timeToScreen(t, viewStart, zoom);
			if (x >= -2 && x <= width + 2) {
				ImGui.getWindowDrawList().addLine(padX + x, screenY, padX + x, screenY + RULER_HEIGHT, ZERO_COLOR, 1f);
				ImGui.getWindowDrawList().addText(padX + x + 2, screenY + 2, ZERO_COLOR, String.format("%.1f", t));
			}
		}
	}

	/** 时间线网格：可见范围内按步长画竖线 */
	private void drawGrid(float contentTopY, float width, double viewStart, double viewEnd, float zoom, float contentHeight) {
		float padX = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
		float screenY0 = ImGui.getWindowPosY() + contentTopY + ImGui.getScrollY();
		double range = Math.max(0.01, viewEnd - viewStart);
		double step = range > 30 ? 5 : (range > 10 ? 2 : (range > 2 ? 1 : 0.5));
		double t0 = Math.floor(viewStart / step) * step;
		for (double t = t0; t <= viewEnd + 0.001; t += step) {
			float x = timeToScreen(t, viewStart, zoom);
			if (x >= 0 && x <= width) {
				ImGui.getWindowDrawList().addLine(padX + x, screenY0, padX + x, screenY0 + contentHeight, GRID_COLOR, 1f);
			}
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

	private float drawAudioWaveformRow(float rowY, Timeline model, float width, float zoom, double viewStart, double viewEnd) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float minX = ImGui.getCursorScreenPosX();
		float minY = ImGui.getCursorScreenPosY();
		WaveformData wf = model.getWaveform();
		if (wf != null && wf.getSampleCount() > 0) {
			double dur = model.getDurationSeconds();
			int samples = (int) Math.min(width, 800);
			float halfH = ROW_HEIGHT * 0.4f;
			for (int i = 0; i < samples; i++) {
				double t = viewStart + (viewEnd - viewStart) * (double) i / samples;
				if (t < 0 || t > dur) continue;
				int idx = wf.timeToIndex(t);
				float s = wf.getSample(idx);
				float x = timeToScreen(t, viewStart, zoom);
				if (x < -1 || x > width + 1) continue;
				float y0 = minY + ROW_HEIGHT * 0.5f;
				float y1 = y0 - s * halfH;
				ImGui.getWindowDrawList().addLine(minX + x, y0, minX + x, y1, WAVEFORM_COLOR, 1f);
			}
		} else {
			ImGui.textDisabled("~~~~ 波形（导入音乐后生成）~~~~");
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawFrequencyDots(float rowY, List<FrequencyEvent> events, float width, float zoom, double viewStart, double viewEnd) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (FrequencyEvent e : events) {
			double t = e.getTimeSeconds();
			if (t < viewStart || t > viewEnd) continue;
			float x = timeToScreen(t, viewStart, zoom);
			if (x >= -4 && x <= width + 4) {
				float r = 3f + e.getEnergy() * 3f;
				ImGui.getWindowDrawList().addCircleFilled(baseX + x, baseY, r, EVENT_DOT_COLOR);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawAnimationEventBlocks(float rowY, List<TimelineAnimationEvent> events, float width, float zoom, double viewStart, double viewEnd, SelectionState selection) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (TimelineAnimationEvent e : events) {
			double t = e.getTimeSeconds();
			double end = e.getEndTimeSeconds();
			if (end < viewStart || t > viewEnd) continue;
			float x = timeToScreen(t, viewStart, zoom);
			float w = (float) (e.getDurationSeconds() * zoom);
			w = Math.max(8f, Math.min(w, width - x + 1));
			if (x + w >= -2 && x <= width + 2) {
				float y0 = baseY - ROW_HEIGHT * 0.35f;
				float y1 = baseY + ROW_HEIGHT * 0.35f;
				ImGui.getWindowDrawList().addRectFilled(baseX + x, y0, baseX + x + w, y1, KEYFRAME_COLOR, 2f);
				if (selection != null && selection.isEventSelected(e.getEventId())) {
					ImGui.getWindowDrawList().addRect(baseX + x, y0, baseX + x + w, y1, SELECTED_BORDER_COLOR, 0f, 0, 2f);
				}
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawCameraKeyframeRow(float rowY, List<CameraKeyframe> keyframes, float width, float zoom, double viewStart, double viewEnd) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (CameraKeyframe k : keyframes) {
			double t = k.getTimeSeconds();
			if (t < viewStart || t > viewEnd) continue;
			float x = timeToScreen(t, viewStart, zoom);
			if (x >= -8 && x <= width + 8) {
				ImGui.getWindowDrawList().addTriangleFilled(
					baseX + x, baseY - 6,
					baseX + x - 5, baseY + 5,
					baseX + x + 5, baseY + 5,
					KEYFRAME_COLOR
				);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawGlobalEventRow(float rowY, List<GlobalEvent> events, float width, float zoom, double viewStart, double viewEnd) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (GlobalEvent e : events) {
			double t = e.getTimeSeconds();
			if (t < viewStart || t > viewEnd) continue;
			float x = timeToScreen(t, viewStart, zoom);
			if (x >= -6 && x <= width + 6) {
				ImGui.getWindowDrawList().addCircleFilled(baseX + x, baseY, 5f, GLOBAL_EVENT_COLOR);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}
}
