package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.SelectionState;

/** 时间线事件点击选择：单选、Ctrl 切换、Shift 范围选。 */
public final class TimelineEventSelectionHandler {

	private TimelineEventSelectionHandler() {}

	public static void applyClickSelection(
		Timeline timeline,
		SelectionState selectionState,
		HitResult hit,
		boolean ctrl,
		boolean shift
	) {
		if (selectionState == null || hit == null) return;

		String eventId = hit.getEventId();
		if (eventId != null) {
			if (ctrl) {
				if (selectionState.isEventSelected(eventId)) {
					selectionState.deselectEvent(eventId);
				} else {
					selectionState.selectEvent(eventId);
				}
				return;
			}
			if (shift) {
				selectEventRange(timeline, selectionState, eventId, hit.getTrackId());
				return;
			}
			selectionState.clearEvents();
			selectionState.clearClips();
			selectionState.selectEvent(eventId);
			selectionState.setRangeAnchorEventId(eventId);
			return;
		}

		String clipId = hit.getClipId();
		if (clipId != null) {
			if (!ctrl) {
				selectionState.clearClips();
				selectionState.clearEvents();
			}
			selectionState.selectClip(clipId);
		}
	}

	private static void selectEventRange(
		Timeline timeline,
		SelectionState selectionState,
		String targetEventId,
		String trackId
	) {
		if (timeline == null || targetEventId == null) return;

		String anchorId = selectionState.getRangeAnchorEventId();
		if (anchorId == null && !selectionState.getSelectedEvents().isEmpty()) {
			anchorId = selectionState.getSelectedEvents().iterator().next();
		}
		if (anchorId == null) {
			anchorId = targetEventId;
		}

		TimelineEventRef anchorRef = TimelineEventRefs.find(timeline, anchorId);
		TimelineEventRef targetRef = TimelineEventRefs.find(timeline, targetEventId);
		if (anchorRef == null || targetRef == null) {
			selectionState.clearEvents();
			selectionState.selectEvent(targetEventId);
			return;
		}

		String resolvedTrackId = trackId != null ? trackId : targetRef.track().getId();
		if (!anchorRef.track().getId().equals(resolvedTrackId)
				|| !targetRef.track().getId().equals(resolvedTrackId)) {
			selectionState.clearEvents();
			selectionState.selectEvent(targetEventId);
			selectionState.setRangeAnchorEventId(targetEventId);
			return;
		}

		double tMin = Math.min(anchorRef.event().getTimeSeconds(), targetRef.event().getTimeSeconds());
		double tMax = Math.max(anchorRef.event().getTimeSeconds(), targetRef.event().getTimeSeconds());

		selectionState.clearEvents();
		Track track = anchorRef.track();
		for (Clip clip : track.getClips()) {
			for (TimelineEvent event : clip.getEvents()) {
				double t = event.getTimeSeconds();
				if (t + 1e-9 >= tMin && t - 1e-9 <= tMax) {
					selectionState.selectEvent(event.getId());
				}
			}
		}
	}
}
