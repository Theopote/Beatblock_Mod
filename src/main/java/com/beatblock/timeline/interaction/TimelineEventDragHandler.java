package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.HitType;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackListState;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.DRAG_THRESHOLD_PX;

/** 事件拖动：按下、拖动、释放提交。 */
public final class TimelineEventDragHandler {

	private TimelineEventDragHandler() {}

	public static TimelineEventDragSession tryBeginFromHit(
		Timeline timeline,
		HitResult hit,
		InteractionState interactionState,
		SelectionState selectionState,
		float mx,
		float my,
		boolean ctrl,
		boolean shift
	) {
		if (timeline == null || hit == null || interactionState == null) return null;
		if (hit.getHitType() != HitType.EVENT && hit.getHitType() != HitType.CLIP) return null;

		TimelineEventDragSession session = TimelineEventDragSession.begin(timeline, hit, interactionState, mx, my);
		if (selectionState != null) {
			TimelineEventSelectionHandler.applyClickSelection(timeline, selectionState, hit, ctrl, shift);
		}
		return session;
	}

	public static void applyDuringDrag(
		Timeline timeline,
		InteractionState interactionState,
		TimelineTrackListState trackListState,
		TimelineViewState viewState,
		TimelineLayout layout,
		TimelineToolbarState toolbarState,
		double duration,
		float mx
	) {
		if (timeline == null || interactionState == null || viewState == null || layout == null) return;
		if (interactionState.getActiveEventId() == null
			|| interactionState.getActiveTrackId() == null
			|| interactionState.getActiveClipId() == null) {
			return;
		}
		if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, interactionState.getActiveTrackId())) {
			return;
		}
		double t = viewState.screenToTime(mx - layout.contentLeft);
		DragController.dragEvent(
			timeline,
			interactionState.getActiveTrackId(),
			interactionState.getActiveClipId(),
			interactionState.getActiveEventId(),
			t,
			duration,
			toolbarState,
			viewState,
			interactionState
		);
	}

	public static void finishOnMouseRelease(
		Timeline timeline,
		TimelineEditor editor,
		TimelineEventDragSession session,
		InteractionState interactionState,
		SelectionState selectionState,
		float mx,
		float my
	) {
		if (session == null || interactionState == null || interactionState.getActiveEventId() == null) return;

		float dx = mx - interactionState.getMouseStartX();
		float dy = my - interactionState.getMouseStartY();
		boolean belowThreshold = dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX;
		if (belowThreshold) {
			TimelineDragCommitSupport.revertEventDrag(timeline, interactionState, session.initialTimeSeconds());
		} else {
			TimelineDragCommitSupport.commitEventDrag(timeline, editor, interactionState, session.initialTimeSeconds());
		}
		session.clear();
	}
}
