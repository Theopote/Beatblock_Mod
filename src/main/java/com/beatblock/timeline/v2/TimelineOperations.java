package com.beatblock.timeline.v2;

import java.util.Map;
import java.util.UUID;

/**
 * 时间线数据操作系统：AddTrack / AddClip / AddEvent / MoveEvent / DeleteEvent，供 UI 与脚本使用。
 */
public final class TimelineOperations {

	private static String nextId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	// --- Track ---

	public static Track addTrack(Timeline timeline, String name, TrackType type) {
		if (timeline == null) return null;
		String id = nextId();
		Track track = new Track(id, name != null ? name : type.name(), type);
		timeline.addTrack(track);
		return track;
	}

	public static boolean removeTrack(Timeline timeline, String trackId) {
		return timeline != null && timeline.removeTrack(trackId);
	}

	// --- Clip ---

	public static Clip addClip(Timeline timeline, String trackId, double startTimeSeconds, double endTimeSeconds) {
		if (timeline == null || trackId == null) return null;
		Track track = timeline.getTrack(trackId);
		if (track == null) return null;
		String id = nextId();
		Clip clip = new Clip(id, startTimeSeconds, endTimeSeconds);
		track.addClip(clip);
		return clip;
	}

	public static Clip addClip(Track track, double startTimeSeconds, double endTimeSeconds) {
		if (track == null) return null;
		String id = nextId();
		Clip clip = new Clip(id, startTimeSeconds, endTimeSeconds);
		track.addClip(clip);
		return clip;
	}

	public static boolean removeClip(Timeline timeline, String trackId, String clipId) {
		if (timeline == null) return false;
		Track track = timeline.getTrack(trackId);
		return track != null && track.removeClip(clipId);
	}

	public static boolean moveClip(Clip clip, double newStartTimeSeconds) {
		if (clip == null) return false;
		double dur = clip.getDurationSeconds();
		clip.setStartTimeSeconds(newStartTimeSeconds);
		clip.setEndTimeSeconds(newStartTimeSeconds + dur);
		return true;
	}

	// --- Event ---

	public static TimelineEvent addEvent(Clip clip, double timeSeconds, EventType type, Map<String, Object> parameters) {
		if (clip == null) return null;
		String id = nextId();
		TimelineEvent event = new TimelineEvent(id, timeSeconds, type, parameters);
		clip.addEvent(event);
		return event;
	}

	public static TimelineEvent addEvent(Timeline timeline, String trackId, String clipId, double timeSeconds, EventType type, Map<String, Object> parameters) {
		if (timeline == null || trackId == null || clipId == null) return null;
		Track track = timeline.getTrack(trackId);
		if (track == null) return null;
		Clip clip = track.getClip(clipId);
		if (clip == null) return null;
		return addEvent(clip, timeSeconds, type, parameters);
	}

	public static boolean removeEvent(Clip clip, String eventId) {
		return clip != null && clip.removeEvent(eventId);
	}

	public static boolean removeEvent(Timeline timeline, String trackId, String clipId, String eventId) {
		if (timeline == null) return false;
		Track track = timeline.getTrack(trackId);
		if (track == null) return false;
		Clip clip = track.getClip(clipId);
		return clip != null && clip.removeEvent(eventId);
	}

	public static boolean moveEvent(TimelineEvent event, double newTimeSeconds) {
		if (event == null) return false;
		event.setTimeSeconds(newTimeSeconds);
		return true;
	}

	public static void setEventTime(TimelineEvent event, double timeSeconds) {
		if (event != null) event.setTimeSeconds(timeSeconds);
	}
}
