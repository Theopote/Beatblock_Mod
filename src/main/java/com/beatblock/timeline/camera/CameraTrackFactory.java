package com.beatblock.timeline.camera;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;

import java.util.HashMap;
import java.util.Map;

/**
 * 在摄像机轨道上创建带 {@link EventType#CAMERA_SEGMENT} 的片段及默认参数。
 */
public final class CameraTrackFactory {

	private static final double DEFAULT_PATH_DURATION = 4.0;
	private static final double DEFAULT_PROC_DURATION = 3.0;

	private CameraTrackFactory() {}

	public static void addPathSegment(Timeline timeline, double timeSeconds,
		double anchorX, double anchorY, double anchorZ, double yawDeg, double pitchDeg) {
		Track t = timeline != null ? timeline.getTrack(Timeline.TRACK_ID_CAMERA) : null;
		if (t == null) return;
		double start = Math.max(0, timeSeconds);
		double end = start + DEFAULT_PATH_DURATION;
		Clip clip = TimelineOperations.addClip(t, start, end);
		if (clip == null) return;
		TimelineOperations.addEvent(clip, start, EventType.CAMERA_SEGMENT, segmentParams(CameraSegmentKind.PATH));
		TimelineOperations.addEvent(clip, start, EventType.CAMERA_KEYFRAME,
			keyframeParams(anchorX, anchorY, anchorZ, yawDeg, pitchDeg, "SMOOTH"));
		TimelineOperations.addEvent(clip, start + 2.0, EventType.CAMERA_KEYFRAME,
			keyframeParams(anchorX + 3.0, anchorY, anchorZ, yawDeg + 25.0, pitchDeg, "SMOOTH"));
		extendDuration(timeline, end);
	}

	public static void addDollySegment(Timeline timeline, double timeSeconds,
		double anchorX, double anchorY, double anchorZ, double yawDeg) {
		addProcSegment(timeline, timeSeconds, CameraSegmentKind.DOLLY, Map.of(
			"anchorX", anchorX,
			"anchorY", anchorY,
			"anchorZ", anchorZ,
			"baseYawDeg", yawDeg,
			"distance0", 2.0,
			"distance1", 8.0
		));
	}

	public static void addOrbitSegment(Timeline timeline, double timeSeconds,
		double anchorX, double anchorY, double anchorZ) {
		addProcSegment(timeline, timeSeconds, CameraSegmentKind.ORBIT, Map.of(
			"anchorX", anchorX,
			"anchorY", anchorY,
			"anchorZ", anchorZ,
			"radius", 10.0,
			"height", 4.0,
			"yawStartDeg", 0.0,
			"yawEndDeg", 270.0
		));
	}

	public static void addCraneSegment(Timeline timeline, double timeSeconds,
		double anchorX, double anchorY, double anchorZ, double yawDeg, double pitchDeg) {
		addProcSegment(timeline, timeSeconds, CameraSegmentKind.CRANE, Map.of(
			"anchorX", anchorX,
			"anchorY", anchorY,
			"anchorZ", anchorZ,
			"yawDeg", yawDeg,
			"pitchDeg", pitchDeg,
			"distance", 12.0,
			"height0", 2.0,
			"height1", 10.0
		));
	}

	public static void addShakeSegment(Timeline timeline, double timeSeconds,
		double anchorX, double anchorY, double anchorZ, double yawDeg, double pitchDeg) {
		addProcSegment(timeline, timeSeconds, CameraSegmentKind.SHAKE, Map.of(
			"anchorX", anchorX,
			"anchorY", anchorY,
			"anchorZ", anchorZ,
			"yawDeg", yawDeg,
			"pitchDeg", pitchDeg,
			"amplitude", 0.35,
			"frequencyHz", 18.0,
			"beatSync", 1.0,
			"beatsPerPulse", 0.5
		));
	}

	private static void addProcSegment(Timeline timeline, double timeSeconds, CameraSegmentKind kind, Map<String, Object> extra) {
		Track t = timeline != null ? timeline.getTrack(Timeline.TRACK_ID_CAMERA) : null;
		if (t == null) return;
		double start = Math.max(0, timeSeconds);
		double end = start + DEFAULT_PROC_DURATION;
		Clip clip = TimelineOperations.addClip(t, start, end);
		if (clip == null) return;
		Map<String, Object> p = segmentParams(kind);
		p.putAll(extra);
		TimelineOperations.addEvent(clip, start, EventType.CAMERA_SEGMENT, p);
		extendDuration(timeline, end);
	}

	private static Map<String, Object> segmentParams(CameraSegmentKind kind) {
		Map<String, Object> m = new HashMap<>();
		m.put("kind", kind.name());
		return m;
	}

	public static Map<String, Object> keyframeParams(double x, double y, double z, double yawDeg, double pitchDeg, String ease) {
		Map<String, Object> m = new HashMap<>();
		m.put("x", x);
		m.put("y", y);
		m.put("z", z);
		m.put("yawDeg", yawDeg);
		m.put("pitchDeg", pitchDeg);
		m.put("ease", ease != null ? ease : "SMOOTH");
		return m;
	}

	private static void extendDuration(Timeline timeline, double endTime) {
		if (timeline == null) return;
		timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), endTime));
	}

	/** 某片段上用于属性面板识别的头事件（时间最早的一条 {@link EventType#CAMERA_SEGMENT}）。 */
	public static TimelineEvent findSegmentHeadEvent(Clip clip) {
		if (clip == null) return null;
		TimelineEvent best = null;
		for (TimelineEvent e : clip.getEvents()) {
			if (e.getType() != EventType.CAMERA_SEGMENT) continue;
			if (best == null || e.getTimeSeconds() < best.getTimeSeconds()) best = e;
		}
		return best;
	}
}
