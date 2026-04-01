package com.beatblock.client.camera;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.editor.TimelineClock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * 摄像机路径关键帧：在播放头或指定时间添加；记录的是当前游戏视角（玩家眼睛位置与朝向），
 * 不经过 {@link TimelineCameraEvaluator} 混合，以便多点连成真实采样的路径。
 */
public final class CameraKeyframeActions {

	private static final double KEYFRAME_MERGE_EPS = 0.04;

	private CameraKeyframeActions() {}

	public static void addKeyframeAtPlayhead(Timeline timeline, TimelineClock clock) {
		if (timeline == null || clock == null) return;
		addKeyframeAtTime(timeline, clock.getCurrentTimeSeconds());
	}

	public static void addKeyframeAtTime(Timeline timeline, double timeSeconds) {
		if (timeline == null) return;
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null) return;
		Clip clip = findActiveClip(cam, timeSeconds);
		if (clip == null) return;
		TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(clip);
		CameraSegmentKind kind = seg != null
			? CameraSegmentKind.fromParam(seg.getParameters().get("kind"))
			: CameraSegmentKind.PATH;
		if (kind != CameraSegmentKind.PATH) return;
		double t = Math.max(clip.getStartTimeSeconds(), Math.min(timeSeconds, clip.getEndTimeSeconds()));
		for (TimelineEvent e : clip.getEvents()) {
			if (e.getType() != EventType.CAMERA_KEYFRAME) continue;
			if (Math.abs(e.getTimeSeconds() - t) < KEYFRAME_MERGE_EPS) return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.gameRenderer == null) return;
		Camera camera = mc.gameRenderer.getCamera();
		if (camera == null) return;
		Vec3d eye = camera.getCameraPos();
		float fy = camera.getYaw();
		float fp = camera.getPitch();
		var params = CameraTrackFactory.keyframeParams(eye.x, eye.y, eye.z, fy, fp, "SMOOTH");
		TimelineOperations.addEvent(clip, t, EventType.CAMERA_KEYFRAME, params);
		timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), clip.getEndTimeSeconds()));
	}

	private static Clip findActiveClip(Track cam, double t) {
		Clip best = null;
		double bestStart = -1;
		for (Clip c : cam.getClips()) {
			if (c == null) continue;
			double s = c.getStartTimeSeconds();
			double e = c.getEndTimeSeconds();
			if (t + 1e-6 < s || t > e + 1e-6) continue;
			if (s > bestStart) {
				bestStart = s;
				best = c;
			}
		}
		return best;
	}

	public static boolean deleteKeyframeEvent(Timeline timeline, String eventId) {
		if (timeline == null || eventId == null || eventId.isBlank()) return false;
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null) return false;
		for (Clip clip : cam.getClips()) {
			if (clip == null) continue;
			for (TimelineEvent e : List.copyOf(clip.getEvents())) {
				if (e.getType() == EventType.CAMERA_KEYFRAME && eventId.equals(e.getId())) {
					return TimelineOperations.removeEvent(clip, eventId);
				}
			}
		}
		return false;
	}
}
