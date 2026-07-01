package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

/** 时间线选中项引用（事件或纯片段）。 */
public record TimelineSelectionRef(Track track, Clip clip, TimelineEvent event) {

	public static TimelineSelectionRef fromEvent(TimelineEventRef ref) {
		return ref != null ? new TimelineSelectionRef(ref.track(), ref.clip(), ref.event()) : null;
	}

	public static TimelineSelectionRef fromClip(Track track, Clip clip) {
		return track != null && clip != null ? new TimelineSelectionRef(track, clip, null) : null;
	}

	public boolean hasEvent() {
		return event != null;
	}
}
