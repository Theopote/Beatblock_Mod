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
		extendDuration(timeline, end);
	}

	/**
	 * 推拉：世界空间直线，起点为当前摄像机（眼点），沿水平朝向前进 {@code reachBlocks} 米到终点。
	 */
	public static void addDollySegment(Timeline timeline, double timeSeconds,
		double startX, double startY, double startZ, double yawDeg, double reachBlocks) {
		double rad = Math.toRadians(-yawDeg);
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		double len = Math.hypot(fx, fz);
		if (len < 1e-6) {
			fx = 0;
			fz = 1;
			len = 1;
		}
		fx /= len;
		fz /= len;
		double r = Math.max(0.05, reachBlocks);
		double endX = startX + fx * r;
		double endZ = startZ + fz * r;
		double endY = startY;
		Map<String, Object> p = new HashMap<>();
		p.put("startX", startX);
		p.put("startY", startY);
		p.put("startZ", startZ);
		p.put("endX", endX);
		p.put("endY", endY);
		p.put("endZ", endZ);
		p.put("baseYawDeg", yawDeg);
		p.put("basePitchDeg", 0.0);
		addProcSegment(timeline, timeSeconds, CameraSegmentKind.DOLLY, p);
	}

	/**
	 * 环绕：目标点（看向）、半径、摄像机相对目标的高度、水平环绕角起止（度，可小于 360）。
	 */
	public static void addOrbitSegment(Timeline timeline, double timeSeconds,
		double targetX, double targetY, double targetZ,
		double radius, double height, double yawStartDeg, double yawEndDeg) {
		Map<String, Object> p = new HashMap<>();
		p.put("targetX", targetX);
		p.put("targetY", targetY);
		p.put("targetZ", targetZ);
		p.put("radius", radius);
		p.put("height", height);
		p.put("yawStartDeg", yawStartDeg);
		p.put("yawEndDeg", yawEndDeg);
		addProcSegment(timeline, timeSeconds, CameraSegmentKind.ORBIT, p);
	}

	/**
	 * 升降：世界空间直线，从当前摄像机眼点到沿 Y 轴平移 {@code deltaY} 的终点；朝向由 yaw/pitch 固定。
	 */
	public static void addCraneSegment(Timeline timeline, double timeSeconds,
		double startX, double startY, double startZ, double yawDeg, double pitchDeg, double deltaY) {
		Map<String, Object> p = new HashMap<>();
		p.put("startX", startX);
		p.put("startY", startY);
		p.put("startZ", startZ);
		p.put("endX", startX);
		p.put("endY", startY + deltaY);
		p.put("endZ", startZ);
		p.put("yawDeg", yawDeg);
		p.put("pitchDeg", pitchDeg);
		addProcSegment(timeline, timeSeconds, CameraSegmentKind.CRANE, p);
	}

	public static void addShakeSegment(Timeline timeline, double timeSeconds,
		double anchorX, double anchorY, double anchorZ, double yawDeg, double pitchDeg) {
		addProcSegment(timeline, timeSeconds, CameraSegmentKind.SHAKE, Map.of(
			"anchorX", anchorX,
			"anchorY", anchorY,
			"anchorZ", anchorZ,
			"yawDeg", yawDeg,
			"pitchDeg", pitchDeg,
			"distance", 10.0,
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
