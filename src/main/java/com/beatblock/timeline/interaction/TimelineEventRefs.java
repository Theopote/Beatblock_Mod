package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.SelectionState;

import java.util.function.Consumer;

public final class TimelineEventRefs {

	private TimelineEventRefs() {}

	public static TimelineEventRef find(Timeline timeline, String eventId) {
		if (timeline == null || eventId == null || eventId.isBlank()) return null;
		for (Track track : timeline.getTracks()) {
			for (Clip clip : track.getClips()) {
				TimelineEvent e = clip.getEvent(eventId);
				if (e != null) return new TimelineEventRef(track, clip, e);
			}
		}
		return null;
	}

	public static TimelineEventRef resolveForProperties(
		Timeline timeline,
		SelectionState selectionState,
		String propertiesEventId,
		Consumer<String> propertiesEventIdSetter
	) {
		TimelineEventRef byContext = find(timeline, propertiesEventId);
		if (byContext != null) return byContext;
		if (selectionState != null && !selectionState.getSelectedEvents().isEmpty()) {
			for (String eventId : selectionState.getSelectedEvents()) {
				TimelineEventRef bySelection = find(timeline, eventId);
				if (bySelection != null) {
					if (propertiesEventIdSetter != null) {
						propertiesEventIdSetter.accept(eventId);
					}
					return bySelection;
				}
			}
		}
		return null;
	}
}
