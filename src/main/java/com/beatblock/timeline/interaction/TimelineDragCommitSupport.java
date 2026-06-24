package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editing.ClipDragStateSnapshot;
import com.beatblock.timeline.editing.TimelineEventEditActions;
import com.beatblock.timeline.editor.InteractionState;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.DRAG_THRESHOLD_PX;

/** 事件/片段拖拽提交与回滚。 */
public final class TimelineDragCommitSupport {

	private TimelineDragCommitSupport() {}

	public static void commitEventDrag(
		Timeline timeline,
		TimelineEditor editor,
		InteractionState interactionState,
		double dragEventInitialTimeSeconds
	) {
		if (editor == null) return;
		TimelineEventRef ref = TimelineEventRefs.find(timeline, interactionState.getActiveEventId());
		if (ref == null || ref.event() == null) return;
		TimelineEventEditActions.commitEventMove(
			timeline,
			editor.getCommandManager(),
			interactionState.getActiveTrackId(),
			interactionState.getActiveClipId(),
			interactionState.getActiveEventId(),
			dragEventInitialTimeSeconds,
			ref.event().getTimeSeconds()
		);
	}

	public static void revertEventDrag(
		Timeline timeline,
		InteractionState interactionState,
		double dragEventInitialTimeSeconds
	) {
		TimelineEventRef ref = TimelineEventRefs.find(timeline, interactionState.getActiveEventId());
		if (ref == null || ref.event() == null) return;
		ref.event().setTimeSeconds(dragEventInitialTimeSeconds);
	}

	public static void commitClipDrag(Timeline timeline, TimelineEditor editor, ClipDragStateSnapshot before) {
		if (before == null) return;
		if (editor == null) return;
		ClipDragStateSnapshot after = before.captureCurrent(timeline);
		TimelineEventEditActions.commitClipDrag(timeline, editor.getCommandManager(), before, after);
	}

	public static void revertClipDrag(Timeline timeline, ClipDragStateSnapshot before) {
		if (before != null) before.applyTo(timeline);
	}

	public static void finishResizeClipDrag(
		Timeline timeline,
		TimelineEditor editor,
		InteractionState interactionState,
		float mx,
		float my,
		ClipDragStateSnapshot resizeClipBeforeSnapshot,
		Runnable clearSnapshot
	) {
		float dx = mx - interactionState.getMouseStartX();
		float dy = my - interactionState.getMouseStartY();
		boolean belowThreshold = dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX;
		if (belowThreshold) {
			revertClipDrag(timeline, resizeClipBeforeSnapshot);
		} else {
			commitClipDrag(timeline, editor, resizeClipBeforeSnapshot);
		}
		if (clearSnapshot != null) clearSnapshot.run();
	}
}
