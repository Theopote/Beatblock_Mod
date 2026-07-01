package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.editor.SelectionState;

import java.util.ArrayList;
import java.util.List;

public final class TimelineEventRefs {

	private TimelineEventRefs() {}

	public static TimelineEventRef find(Timeline timeline, String eventId) {
		if (timeline == null || eventId == null || eventId.isBlank()) {
			return null;
		}
		for (Track track : timeline.getTracks()) {
			for (Clip clip : track.getClips()) {
				TimelineEvent event = clip.getEvent(eventId);
				if (event != null) {
					return new TimelineEventRef(track, clip, event);
				}
			}
		}
		return null;
	}

	/**
	 * 从 {@link SelectionState} 解析属性编辑目标：优先选中事件，否则首个选中片段。
	 */
	public static TimelineSelectionRef resolveFromSelection(Timeline timeline, SelectionState selectionState) {
		if (timeline == null || selectionState == null) {
			return null;
		}
		if (!selectionState.getSelectedEvents().isEmpty()) {
			List<String> eventIds = new ArrayList<>(selectionState.getSelectedEvents());
			eventIds.sort(String::compareTo);
			for (String eventId : eventIds) {
				TimelineEventRef ref = find(timeline, eventId);
				if (ref != null) {
					return TimelineSelectionRef.fromEvent(ref);
				}
			}
		}
		if (!selectionState.getSelectedClips().isEmpty()) {
			List<String> clipIds = new ArrayList<>(selectionState.getSelectedClips());
			clipIds.sort(String::compareTo);
			for (String clipId : clipIds) {
				if (clipId == null || clipId.isBlank()) {
					continue;
				}
				for (Track track : timeline.getTracks()) {
					Clip clip = track.getClip(clipId);
					if (clip == null) {
						continue;
					}
					if (Timeline.TRACK_ID_CAMERA.equals(track.getId())) {
						TimelineEvent segmentHead = CameraTrackFactory.findSegmentHeadEvent(clip);
						if (segmentHead != null) {
							return TimelineSelectionRef.fromEvent(new TimelineEventRef(track, clip, segmentHead));
						}
					}
					return TimelineSelectionRef.fromClip(track, clip);
				}
			}
		}
		return null;
	}
}
