package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.InteractionMode;
import com.beatblock.timeline.editor.InteractionState;

/**
 * Distinguishes live interaction preview mutations from committed document mutations.
 * <p>
 * During drag/resize, the live Timeline is mutated for preview. Cancel restores the
 * gesture-start snapshot without going through CommandManager.
 * Commit (mouseup past threshold) remains the only path that creates Undo history
 * and fires {@link com.beatblock.timeline.editing.TimelineDocumentChangeNotifier}.
 */
public final class TimelineGestureLifecycle {

	private TimelineGestureLifecycle() {}

	public static boolean isLiveDocumentPreview(InteractionMode mode) {
		return mode == InteractionMode.DRAG_EVENT
			|| mode == InteractionMode.DRAG_CLIP
			|| mode == InteractionMode.RESIZE_CLIP
			|| mode == InteractionMode.MARKER_DRAG;
	}

	/**
	 * Revert live preview mutations and clear gesture state.
	 * No-op when not in a document-mutating preview gesture.
	 */
	public static void cancelLiveDocumentPreview(
		Timeline timeline,
		InteractionState interactionState,
		TimelineEventDragSession eventDragSession,
		TimelineClipDragSession clipDragSession,
		TimelineCameraClipResizeHandler.Session cameraResizeSession,
		Runnable clearEventSession,
		Runnable clearClipSession,
		Runnable clearCameraSession
	) {
		if (interactionState == null) {
			return;
		}
		InteractionMode mode = interactionState.getMode();
		if (!isLiveDocumentPreview(mode)) {
			return;
		}

		if (mode == InteractionMode.DRAG_EVENT) {
			if (eventDragSession != null) {
				TimelineDragCommitSupport.revertEventDrag(
					timeline, interactionState, eventDragSession.initialTimeSeconds());
				eventDragSession.clear();
			}
			if (clearEventSession != null) {
				clearEventSession.run();
			}
		} else if (mode == InteractionMode.DRAG_CLIP) {
			if (clipDragSession != null) {
				TimelineDragCommitSupport.revertClipDrag(timeline, clipDragSession.undoSnapshot());
				clipDragSession.clear();
			}
			if (clearClipSession != null) {
				clearClipSession.run();
			}
		} else if (mode == InteractionMode.RESIZE_CLIP) {
			if (cameraResizeSession != null) {
				TimelineDragCommitSupport.revertClipDrag(timeline, cameraResizeSession.undoSnapshot());
			}
			if (clearCameraSession != null) {
				clearCameraSession.run();
			}
		} else if (mode == InteractionMode.MARKER_DRAG) {
			var before = interactionState.getMarkerDragBefore();
			if (timeline != null && before != null) {
				timeline.replaceMarker(before);
			}
			interactionState.clearAlignmentGuideTimes();
		}

		interactionState.setMode(InteractionMode.NONE);
		interactionState.clearActive();
	}
}
