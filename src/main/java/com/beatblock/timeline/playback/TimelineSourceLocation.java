package com.beatblock.timeline.playback;

/** Stable address of an editable event in its source timeline. */
public record TimelineSourceLocation(
	String trackId,
	String clipId,
	String eventId,
	int sourceIndex
) {
	public TimelineSourceLocation {
		trackId = trackId != null ? trackId : "";
		clipId = clipId != null ? clipId : "";
		eventId = eventId != null ? eventId : "";
		if (sourceIndex < 0) throw new IllegalArgumentException("sourceIndex must be non-negative");
	}
}