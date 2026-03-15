package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.*;
import com.beatblock.timeline.rendering.TimelineLayout;
import imgui.ImGui;

/**
 * 时间线输入：鼠标按下/拖拽/释放，驱动 InteractionState、SelectionState、Clock。
 */
public final class TimelineInteraction {

	private static final float RULER_HEIGHT = 20f;
	private static final float ROW_HEIGHT = 22f;
	private static final float DRAG_THRESHOLD_PX = 4f;

	private static final String[] INTERACTIVE_TRACK_IDS = {
		Timeline.TRACK_ID_ANIMATION_BLOCK,
		Timeline.TRACK_ID_ANIMATION_AUTO,
		Timeline.TRACK_ID_CAMERA,
		Timeline.TRACK_ID_GLOBAL
	};

	/** 四行可交互轨道在内容区的行偏移（相对 baseContentScreenY） */
	private static final float[] ROW_OFFSETS = { 5 * ROW_HEIGHT, 6 * ROW_HEIGHT, 8 * ROW_HEIGHT, 10 * ROW_HEIGHT };

	public void update(
		Timeline timeline,
		TimelineViewState viewState,
		InteractionState interactionState,
		SelectionState selectionState,
		TimelineClock clock,
		SelectionBox selectionBox,
		float contentLeft,
		float contentWidth,
		float rulerScreenTop,
		float rulerScreenBottom,
		float baseContentScreenY
	) {
		if (timeline == null || viewState == null || interactionState == null || selectionState == null) return;
		if (!ImGui.isWindowHovered()) return;

		float mx = ImGui.getMousePosX();
		float my = ImGui.getMousePosY();
		double duration = timeline.getDurationSeconds();
		if (duration <= 0) duration = 60.0;

		// 鼠标释放
		if (ImGui.isMouseReleased(0)) {
			if (interactionState.getMode() == InteractionMode.DRAG_EVENT && interactionState.getActiveEventId() != null) {
				float dx = mx - interactionState.getMouseStartX();
				float dy = my - interactionState.getMouseStartY();
				if (dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) {
					selectionState.clearEvents();
					selectionState.selectEvent(interactionState.getActiveEventId());
				}
			}
			if (interactionState.getMode() == InteractionMode.BOX_SELECT) {
				// 框选解析可在此扩展
			}
			interactionState.setMode(InteractionMode.NONE);
			interactionState.clearActive();
			if (selectionBox != null) selectionBox.setActive(false);
			return;
		}

		// 拖拽中
		if (ImGui.isMouseDown(0) && interactionState.getMode() != InteractionMode.NONE) {
			if (interactionState.getMode() == InteractionMode.SCRUB_TIME && clock != null) {
				double t = viewState.screenToTime(mx - contentLeft);
				clock.seek(Math.max(0, Math.min(t, duration)));
				return;
			}
			if (interactionState.getMode() == InteractionMode.DRAG_EVENT && interactionState.getActiveEventId() != null
				&& interactionState.getActiveTrackId() != null && interactionState.getActiveClipId() != null) {
				double t = viewState.screenToTime(mx - contentLeft);
				DragController.dragEvent(timeline, interactionState.getActiveTrackId(), interactionState.getActiveClipId(), interactionState.getActiveEventId(), t, duration);
				return;
			}
			return;
		}

		// 鼠标按下：HitTest
		if (ImGui.isMouseClicked(0)) {
			boolean ctrl = ImGui.getIO().getKeyCtrl();
			HitResult rulerHit = HitTestSystem.hitTestTimeRuler(mx, my, contentLeft, rulerScreenTop, RULER_HEIGHT, contentWidth, viewState);
			if (!rulerHit.isEmpty() && rulerHit.getHitType() == HitType.TIME_HEADER) {
				interactionState.setMode(InteractionMode.SCRUB_TIME);
				interactionState.setMouseStart(mx, my);
				if (clock != null) clock.seek(Math.max(0, Math.min(rulerHit.getTimeSeconds(), duration)));
				return;
			}
			for (int i = 0; i < INTERACTIVE_TRACK_IDS.length && i < ROW_OFFSETS.length; i++) {
				float rowScreenY = baseContentScreenY + ROW_OFFSETS[i];
				HitResult hit = HitTestSystem.hitTestTrackContent(timeline, INTERACTIVE_TRACK_IDS[i], mx, my, contentLeft, rowScreenY, ROW_HEIGHT, contentWidth, viewState);
				if (hit.isEmpty()) continue;
				if (hit.getHitType() == HitType.EVENT || hit.getHitType() == HitType.CLIP) {
					interactionState.setMode(InteractionMode.DRAG_EVENT);
					interactionState.setMouseStart(mx, my);
					interactionState.setActiveEventId(hit.getEventId());
					interactionState.setActiveClipId(hit.getClipId());
					interactionState.setActiveTrackId(hit.getTrackId());
					if (!ctrl) selectionState.clearEvents();
					if (hit.getEventId() != null) selectionState.selectEvent(hit.getEventId());
					else if (hit.getClipId() != null) selectionState.selectClip(hit.getClipId());
					return;
				}
			}
			selectionState.clearEvents();
			selectionState.clearClips();
			if (selectionBox != null) {
				selectionBox.setStart(mx, my);
				selectionBox.setEnd(mx, my);
				selectionBox.setActive(true);
			}
			interactionState.setMode(InteractionMode.BOX_SELECT);
			interactionState.setMouseStart(mx, my);
		}
		if (interactionState.getMode() == InteractionMode.BOX_SELECT && ImGui.isMouseDown(0) && selectionBox != null) {
			selectionBox.setEnd(mx, my);
		}
	}
}
