package com.beatblock.client.camera;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 根据时间线摄像机轨计算当前时刻的世界坐标与 yaw/pitch（度）。
 * 播放驱动 {@link com.beatblock.client.BeatBlockClientDriver#isDriving()} 时由 {@link com.beatblock.mixin.client.CameraMixin} 应用。
 */
public final class TimelineCameraEvaluator {

	public record CameraSample(Vec3d position, float yawDeg, float pitchDeg) {}

	private TimelineCameraEvaluator() {}

	public static CameraSample evaluate(Timeline timeline, double timeSeconds, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		if (timeline == null) return null;
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null || cam.getClips().isEmpty()) return null;

		Clip active = findActiveClip(cam, timeSeconds);
		if (active != null) {
			TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(active);
			if (seg != null) {
				CameraSegmentKind kind = CameraSegmentKind.fromParam(seg.getParameters().get("kind"));
				CameraSample s = evaluateSegment(timeline, active, seg, kind, timeSeconds, anchor, fallbackYaw, fallbackPitch);
				if (s != null) return s;
			}
			CameraSample legacy = evaluateKeyframeHoldInClip(active, timeSeconds, anchor, fallbackYaw, fallbackPitch);
			if (legacy != null) return legacy;
		}
		return evaluateGlobalKeyframes(cam, timeSeconds, anchor, fallbackYaw, fallbackPitch);
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

	private static CameraSample evaluateSegment(Timeline timeline, Clip clip, TimelineEvent seg, CameraSegmentKind kind, double timeSeconds,
		Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		double s0 = clip.getStartTimeSeconds();
		double s1 = clip.getEndTimeSeconds();
		double dur = Math.max(1e-3, s1 - s0);
		double u = (timeSeconds - s0) / dur;
		u = Math.max(0.0, Math.min(1.0, u));
		Map<String, Object> p = seg.getParameters();
		double bpm = timeline != null ? timeline.getBpm() : 0;

		return switch (kind) {
			case PATH -> evaluatePath(clip, timeSeconds, anchor, fallbackYaw, fallbackPitch);
			case DOLLY -> evaluateDolly(p, u, anchor, fallbackYaw, fallbackPitch);
			case ORBIT -> evaluateOrbit(p, u, anchor, fallbackYaw, fallbackPitch);
			case CRANE -> evaluateCrane(p, u, anchor, fallbackYaw, fallbackPitch);
			case SHAKE -> evaluateShake(p, timeSeconds, anchor, fallbackYaw, fallbackPitch, bpm);
		};
	}

	private static CameraSample evaluatePath(Clip clip, double timeSeconds, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		List<TimelineEvent> kf = new ArrayList<>();
		for (TimelineEvent e : clip.getEvents()) {
			if (e.getType() == EventType.CAMERA_KEYFRAME) kf.add(e);
		}
		kf.sort(Comparator.comparingDouble(TimelineEvent::getTimeSeconds));
		if (kf.isEmpty()) return new CameraSample(anchor, fallbackYaw, fallbackPitch);
		if (kf.size() == 1) return sampleKeyframe(kf.getFirst(), anchor, fallbackYaw, fallbackPitch);
		if (timeSeconds <= kf.getFirst().getTimeSeconds() + 1e-6) {
			return sampleKeyframe(kf.getFirst(), anchor, fallbackYaw, fallbackPitch);
		}
		if (timeSeconds >= kf.getLast().getTimeSeconds() - 1e-6) {
			return sampleKeyframe(kf.getLast(), anchor, fallbackYaw, fallbackPitch);
		}
		for (int i = 0; i < kf.size() - 1; i++) {
			TimelineEvent a = kf.get(i);
			TimelineEvent b = kf.get(i + 1);
			double ta = a.getTimeSeconds();
			double tb = b.getTimeSeconds();
			if (timeSeconds < ta || timeSeconds > tb) continue;
			double span = Math.max(1e-6, tb - ta);
			double t = (timeSeconds - ta) / span;
			String ease = stringParam(a.getParameters(), "ease", "SMOOTH");
			double wt = "LINEAR".equalsIgnoreCase(ease) ? t : smoothstep(t);
			return blendKeyframes(a, b, wt, anchor, fallbackYaw, fallbackPitch);
		}
		return sampleKeyframe(kf.getFirst(), anchor, fallbackYaw, fallbackPitch);
	}

	private static CameraSample evaluateKeyframeHoldInClip(Clip clip, double timeSeconds, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		List<TimelineEvent> kf = new ArrayList<>();
		for (TimelineEvent e : clip.getEvents()) {
			if (e.getType() == EventType.CAMERA_KEYFRAME) kf.add(e);
		}
		if (kf.isEmpty()) return null;
		kf.sort(Comparator.comparingDouble(TimelineEvent::getTimeSeconds));
		TimelineEvent pick = kf.getFirst();
		for (TimelineEvent e : kf) {
			if (e.getTimeSeconds() <= timeSeconds + 1e-6) pick = e;
			else break;
		}
		return sampleKeyframe(pick, anchor, fallbackYaw, fallbackPitch);
	}

	private static CameraSample evaluateGlobalKeyframes(Track cam, double timeSeconds, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		List<TimelineEvent> kf = new ArrayList<>();
		for (Clip c : cam.getClips()) {
			for (TimelineEvent e : c.getEvents()) {
				if (e.getType() == EventType.CAMERA_KEYFRAME) kf.add(e);
			}
		}
		if (kf.isEmpty()) return null;
		kf.sort(Comparator.comparingDouble(TimelineEvent::getTimeSeconds));
		if (timeSeconds <= kf.getFirst().getTimeSeconds() + 1e-6) {
			return sampleKeyframe(kf.getFirst(), anchor, fallbackYaw, fallbackPitch);
		}
		if (timeSeconds >= kf.getLast().getTimeSeconds() - 1e-6) {
			return sampleKeyframe(kf.getLast(), anchor, fallbackYaw, fallbackPitch);
		}
		for (int i = 0; i < kf.size() - 1; i++) {
			TimelineEvent a = kf.get(i);
			TimelineEvent b = kf.get(i + 1);
			double ta = a.getTimeSeconds();
			double tb = b.getTimeSeconds();
			if (timeSeconds < ta || timeSeconds > tb) continue;
			double span = Math.max(1e-6, tb - ta);
			double t = (timeSeconds - ta) / span;
			String ease = stringParam(a.getParameters(), "ease", "LINEAR");
			double wt = "LINEAR".equalsIgnoreCase(ease) ? t : smoothstep(t);
			return blendKeyframes(a, b, wt, anchor, fallbackYaw, fallbackPitch);
		}
		return sampleKeyframe(kf.getFirst(), anchor, fallbackYaw, fallbackPitch);
	}

	private static CameraSample evaluateDolly(Map<String, Object> p, double u, Vec3d anchor, float yawDeg, float pitchDeg) {
		double ax = num(p, "anchorX", anchor.x);
		double ay = num(p, "anchorY", anchor.y);
		double az = num(p, "anchorZ", anchor.z);
		double baseYaw = (float) num(p, "baseYawDeg", yawDeg);
		Vec3d forward = horizontalForward((float) baseYaw);
		double d0 = num(p, "distance0", 2.0);
		double d1 = num(p, "distance1", 8.0);
		double d = lerp(d0, d1, smoothstep(u));
		Vec3d pos = new Vec3d(ax, ay, az).add(forward.multiply(d));
		return new CameraSample(pos, (float) baseYaw, pitchDeg);
	}

	private static CameraSample evaluateOrbit(Map<String, Object> p, double u, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		double ax = num(p, "anchorX", anchor.x);
		double ay = num(p, "anchorY", anchor.y);
		double az = num(p, "anchorZ", anchor.z);
		double radius = num(p, "radius", 10.0);
		double height = num(p, "height", 4.0);
		double y0 = num(p, "yawStartDeg", 0.0);
		double y1 = num(p, "yawEndDeg", 270.0);
		double yaw = lerp(y0, y1, u);
		double rad = Math.toRadians(-yaw);
		double ox = -Math.sin(rad) * radius;
		double oz = Math.cos(rad) * radius;
		Vec3d pos = new Vec3d(ax + ox, ay + height, az + oz);
		float pitch = (float) Math.toDegrees(-Math.atan2(height, radius));
		return new CameraSample(pos, (float) yaw, pitch);
	}

	private static CameraSample evaluateCrane(Map<String, Object> p, double u, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		double ax = num(p, "anchorX", anchor.x);
		double ay = num(p, "anchorY", anchor.y);
		double az = num(p, "anchorZ", anchor.z);
		double yaw = num(p, "yawDeg", fallbackYaw);
		double pitch = num(p, "pitchDeg", fallbackPitch);
		double dist = num(p, "distance", 12.0);
		double h0 = num(p, "height0", 2.0);
		double h1 = num(p, "height1", 10.0);
		double h = lerp(h0, h1, smoothstep(u));
		Vec3d forward = horizontalForward((float) yaw);
		Vec3d base = new Vec3d(ax, ay, az).add(forward.multiply(dist));
		Vec3d pos = base.add(0, h, 0);
		return new CameraSample(pos, (float) yaw, (float) pitch);
	}

	private static CameraSample evaluateShake(Map<String, Object> p, double timeSeconds, Vec3d anchor,
		float fallbackYaw, float fallbackPitch, double bpm) {
		double ax = num(p, "anchorX", anchor.x);
		double ay = num(p, "anchorY", anchor.y);
		double az = num(p, "anchorZ", anchor.z);
		double yaw = num(p, "yawDeg", fallbackYaw);
		double pitch = num(p, "pitchDeg", fallbackPitch);
		double dist = num(p, "distance", 10.0);
		Vec3d forward = horizontalForward((float) yaw);
		Vec3d pos = new Vec3d(ax, ay, az).add(forward.multiply(dist));
		double amp = num(p, "amplitude", 0.35);
		double freq = num(p, "frequencyHz", 18.0);
		double beatSync = num(p, "beatSync", 0.0);
		double beatsPerPulse = num(p, "beatsPerPulse", 0.5);
		double phase = timeSeconds * freq * Math.PI * 2.0;
		if (beatSync > 0.5) {
			double beatDur = bpm > 1e-3 ? 60.0 / bpm : 0.5;
			phase = (timeSeconds / beatDur) * Math.PI * 2.0 * beatsPerPulse;
		}
		double ox = Math.sin(phase) * amp;
		double oy = Math.cos(phase * 1.31) * amp * 0.6;
		double oz = Math.sin(phase * 0.73 + 1.2) * amp * 0.5;
		Vec3d shaken = pos.add(ox, oy, oz);
		return new CameraSample(shaken, (float) yaw, (float) pitch);
	}

	private static CameraSample blendKeyframes(TimelineEvent a, TimelineEvent b, double w, Vec3d anchor, float fy, float fp) {
		CameraSample sa = sampleKeyframe(a, anchor, fy, fp);
		CameraSample sb = sampleKeyframe(b, anchor, fy, fp);
		Vec3d p = sa.position().lerp(sb.position(), w);
		float yaw = (float) lerp(sa.yawDeg(), sb.yawDeg(), w);
		float pitch = (float) lerp(sa.pitchDeg(), sb.pitchDeg(), w);
		return new CameraSample(p, yaw, pitch);
	}

	private static CameraSample sampleKeyframe(TimelineEvent e, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		Map<String, Object> p = e.getParameters();
		double x = num(p, "x", anchor.x);
		double y = num(p, "y", anchor.y);
		double z = num(p, "z", anchor.z);
		float yaw = (float) num(p, "yawDeg", fallbackYaw);
		float pitch = (float) num(p, "pitchDeg", fallbackPitch);
		return new CameraSample(new Vec3d(x, y, z), yaw, pitch);
	}

	private static Vec3d horizontalForward(float yawDeg) {
		float rad = (float) Math.toRadians(-yawDeg);
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		double len = Math.sqrt(fx * fx + fz * fz);
		if (len < 1e-6) return new Vec3d(0, 0, 1);
		return new Vec3d(fx / len, 0, fz / len);
	}

	private static double smoothstep(double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	private static double num(Map<String, Object> p, String key, double def) {
		if (p == null) return def;
		Object o = p.get(key);
		if (o instanceof Number n) return n.doubleValue();
		if (o != null) {
			try {
				return Double.parseDouble(String.valueOf(o).trim());
			} catch (Exception ignored) {
				return def;
			}
		}
		return def;
	}

	private static String stringParam(Map<String, Object> p, String key, String def) {
		if (p == null) return def;
		Object o = p.get(key);
		if (o == null) return def;
		String s = String.valueOf(o).trim();
		return s.isEmpty() ? def : s;
	}
}
