package com.beatblock.timeline;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * 时间线数据操作：AddTrack / AddClip / AddEvent / MoveEvent / DeleteEvent。
 */
public final class TimelineOperations {

	private TimelineOperations() {}

	private static String nextId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	public static @Nullable Track addTrack(@Nullable Timeline timeline, @Nullable String name, @Nullable TrackType type) {
		if (timeline == null) return null;
		String id = nextId();
		TrackType resolvedType = type != null ? type : TrackType.ANIMATION;
		Track track = new Track(id, name != null ? name : resolvedType.name(), resolvedType);
		timeline.addTrack(track);
		return track;
	}

	public static boolean removeTrack(@Nullable Timeline timeline, @Nullable String trackId) {
		return timeline != null && timeline.removeTrack(trackId);
	}

	public static @Nullable Clip addClip(
		@Nullable Timeline timeline,
		@Nullable String trackId,
		double startTimeSeconds,
		double endTimeSeconds
	) {
		if (timeline == null || trackId == null) return null;
		Track track = timeline.getTrack(trackId);
		return track != null ? addClip(track, startTimeSeconds, endTimeSeconds) : null;
	}

	public static @Nullable Clip addClip(@Nullable Track track, double startTimeSeconds, double endTimeSeconds) {
		if (track == null) return null;
		String id = nextId();
		Clip clip = new Clip(id, startTimeSeconds, endTimeSeconds);
		track.addClip(clip);
		return clip;
	}

	public static boolean removeClip(@Nullable Timeline timeline, @Nullable String trackId, @Nullable String clipId) {
		if (timeline == null) return false;
		Track track = timeline.getTrack(trackId);
		return track != null && track.removeClip(clipId);
	}

	public static boolean moveClip(@Nullable Clip clip, double newStartTimeSeconds) {
		if (clip == null) return false;
		double dur = clip.getDurationSeconds();
		clip.setStartTimeSeconds(newStartTimeSeconds);
		clip.setEndTimeSeconds(newStartTimeSeconds + dur);
		return true;
	}

	public static @Nullable TimelineEvent addEvent(
		@Nullable Clip clip,
		double timeSeconds,
		@Nullable EventType type,
		@Nullable Map<String, Object> parameters
	) {
		if (clip == null) return null;
		String id = nextId();
		TimelineEvent event = new TimelineEvent(id, timeSeconds, type, parameters);
		clip.addEvent(event);
		return event;
	}

	public static @Nullable TimelineEvent addEvent(
		@Nullable Timeline timeline,
		@Nullable String trackId,
		@Nullable String clipId,
		double timeSeconds,
		@Nullable EventType type,
		@Nullable Map<String, Object> parameters
	) {
		if (timeline == null || trackId == null || clipId == null) return null;
		Track track = timeline.getTrack(trackId);
		if (track == null) return null;
		Clip clip = track.getClip(clipId);
		return clip != null ? addEvent(clip, timeSeconds, type, parameters) : null;
	}

	public static boolean removeEvent(@Nullable Clip clip, @Nullable String eventId) {
		return clip != null && clip.removeEvent(eventId);
	}

	public static boolean moveEvent(@Nullable TimelineEvent event, double newTimeSeconds) {
		if (event == null) return false;
		event.setTimeSeconds(newTimeSeconds);
		return true;
	}
}
