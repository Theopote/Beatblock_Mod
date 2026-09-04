package com.beatblock.client.camera;

import com.beatblock.BeatBlock;
import com.beatblock.automap.camera.CameraEasing;
import com.beatblock.automap.camera.CameraSegmentSemantics;
import com.beatblock.automap.camera.CameraShotEasing;
import com.beatblock.automap.camera.CameraShotTransition;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.playback.CompiledCameraTrack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 根据时间线摄像机轨计算当前时刻的世界坐标与 yaw/pitch（度）。
 * 时间线正在播放时由 {@link com.beatblock.mixin.client.CameraMixin} 应用（见 {@link com.beatblock.client.BeatBlockClientDriver#shouldApplyTimelineCameraToView()}）。
 */
public final class TimelineCameraEvaluator {

	public record CameraSample(Vec3d position, float yawDeg, float pitchDeg) {}

	private TimelineCameraEvaluator() {}

	public static CameraSample evaluate(Timeline timeline, double timeSeconds, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		return evaluate(timeline, timeSeconds, anchor, fallbackYaw, fallbackPitch, true);
	}

	private static CameraSample evaluate(
		Timeline timeline,
		double timeSeconds,
		Vec3d anchor,
		float fallbackYaw,
		float fallbackPitch,
		boolean blendTransition
	) {
		if (timeline == null) return null;
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null || cam.getClips().isEmpty()) return null;

		Clip active = findActiveClip(cam, timeSeconds);
		if (active != null) {
			TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(active);
			if (seg != null) {
				CameraSegmentKind kind = CameraSegmentKind.fromParam(seg.getParameters().get("kind"));
				CameraSample s = evaluateSegment(timeline, active, seg, kind, timeSeconds, anchor, fallbackYaw, fallbackPitch);
				if (s != null) {
					return blendTransition
						? applyIncomingTransition(cam, active, seg, timeSeconds, s, timeline, anchor, fallbackYaw, fallbackPitch)
						: s;
				}
			}
			CameraSample legacy = evaluateKeyframeHoldInClip(active, timeSeconds, anchor, fallbackYaw, fallbackPitch);
			if (legacy != null) return legacy;
		}
		return evaluateGlobalKeyframes(cam, timeSeconds, anchor, fallbackYaw, fallbackPitch);
	}

	public static CameraSample evaluate(CompiledCameraTrack track, double bpm, double timeSeconds,
		Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		return evaluate(track, bpm, timeSeconds, anchor, fallbackYaw, fallbackPitch, true);
	}

	private static CameraSample evaluate(
		CompiledCameraTrack track,
		double bpm,
		double timeSeconds,
		Vec3d anchor,
		float fallbackYaw,
		float fallbackPitch,
		boolean blendTransition
	) {
		if (track == null || track.isEmpty()) return null;
		CompiledCameraTrack.CameraClip active = findActiveClip(track, timeSeconds);
		if (active != null) {
			CompiledCameraTrack.CameraEvent segment = findSegmentHeadEvent(active);
			if (segment != null) {
				CameraSegmentKind kind = CameraSegmentKind.fromParam(segment.parameters().get("kind"));
				CameraSample sample = evaluateSegment(
					active, segment, kind, bpm, timeSeconds, anchor, fallbackYaw, fallbackPitch);
				if (sample != null) {
					return blendTransition
						? applyIncomingTransition(track, active, segment, timeSeconds, sample, bpm, anchor, fallbackYaw, fallbackPitch)
						: sample;
				}
			}
			CameraSample legacy = evaluateKeyframeHoldInClip(active, timeSeconds, anchor, fallbackYaw, fallbackPitch);
			if (legacy != null) return legacy;
		}
		return evaluateGlobalKeyframes(track, timeSeconds, anchor, fallbackYaw, fallbackPitch);
	}

	private static CompiledCameraTrack.CameraClip findActiveClip(CompiledCameraTrack track, double timeSeconds) {
		CompiledCameraTrack.CameraClip best = null;
		double bestStart = -1;
		for (var clip : track.clips()) {
			if (timeSeconds + 1e-6 < clip.startTimeSeconds() || timeSeconds > clip.endTimeSeconds() + 1e-6) continue;
			if (clip.startTimeSeconds() > bestStart) {
				bestStart = clip.startTimeSeconds();
				best = clip;
			}
		}
		return best;
	}

	private static CompiledCameraTrack.CameraEvent findSegmentHeadEvent(CompiledCameraTrack.CameraClip clip) {
		for (var event : clip.events()) {
			if (event.type() == EventType.CAMERA_SEGMENT) return event;
		}
		return null;
	}

	private static CameraSample evaluateSegment(CompiledCameraTrack.CameraClip clip,
		CompiledCameraTrack.CameraEvent segment, CameraSegmentKind kind, double bpm, double timeSeconds,
		Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		double duration = Math.max(1e-3, clip.endTimeSeconds() - clip.startTimeSeconds());
		double u = Math.max(0.0, Math.min(1.0, (timeSeconds - clip.startTimeSeconds()) / duration));
		Map<String, Object> params = segment.parameters();
		double easedU = CameraEasing.apply(u, CameraSegmentSemantics.easingFrom(params));
		Map<String, Object> spatial = CameraSegmentSemantics.withFollowDeltaApplied(params);
		CameraSample sample = switch (kind) {
			case PATH -> evaluatePath(clip, timeSeconds, anchor, fallbackYaw, fallbackPitch, params);
			case DOLLY -> evaluateDolly(spatial, easedU, anchor, fallbackYaw, fallbackPitch);
			case ORBIT -> evaluateOrbit(spatial, easedU, anchor, fallbackYaw, fallbackPitch);
			case CRANE -> evaluateCrane(spatial, easedU, anchor, fallbackYaw, fallbackPitch);
			case SHAKE -> evaluateShake(spatial, timeSeconds, anchor, fallbackYaw, fallbackPitch, bpm);
		};
		return finalizeSample(sample, params, kind == CameraSegmentKind.PATH);
	}

	private static CameraSample evaluatePath(CompiledCameraTrack.CameraClip clip, double timeSeconds,
		Vec3d anchor, float fallbackYaw, float fallbackPitch, Map<String, Object> segmentParams) {
		List<CompiledCameraTrack.CameraEvent> keyframes = cameraKeyframes(clip.events());
		if (keyframes.isEmpty()) return finalizeSample(new CameraSample(anchor, fallbackYaw, fallbackPitch), segmentParams, true);
		if (keyframes.size() == 1 || timeSeconds <= keyframes.getFirst().timeSeconds() + 1e-6)
			return finalizeSample(sampleKeyframe(keyframes.getFirst(), anchor, fallbackYaw, fallbackPitch), segmentParams, true);
		if (timeSeconds >= keyframes.getLast().timeSeconds() - 1e-6)
			return finalizeSample(sampleKeyframe(keyframes.getLast(), anchor, fallbackYaw, fallbackPitch), segmentParams, true);
		for (int i = 0; i < keyframes.size() - 1; i++) {
			var a = keyframes.get(i);
			var b = keyframes.get(i + 1);
			if (timeSeconds < a.timeSeconds() - 1e-6 || timeSeconds > b.timeSeconds() + 1e-6) continue;
			double t = (timeSeconds - a.timeSeconds()) / Math.max(1e-6, b.timeSeconds() - a.timeSeconds());
			double weight = CameraEasing.apply(t, stringParam(a.parameters(), "ease", "SMOOTH"));
			return finalizeSample(blendKeyframes(a, b, weight, anchor, fallbackYaw, fallbackPitch), segmentParams, true);
		}
		return finalizeSample(sampleKeyframe(keyframes.getFirst(), anchor, fallbackYaw, fallbackPitch), segmentParams, true);
	}

	private static CameraSample evaluateKeyframeHoldInClip(CompiledCameraTrack.CameraClip clip, double timeSeconds,
		Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		List<CompiledCameraTrack.CameraEvent> keyframes = cameraKeyframes(clip.events());
		if (keyframes.isEmpty()) return null;
		var selected = keyframes.getFirst();
		for (var event : keyframes) {
			if (event.timeSeconds() <= timeSeconds + 1e-6) selected = event;
			else break;
		}
		return sampleKeyframe(selected, anchor, fallbackYaw, fallbackPitch);
	}

	private static CameraSample evaluateGlobalKeyframes(CompiledCameraTrack track, double timeSeconds,
		Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		List<CompiledCameraTrack.CameraEvent> keyframes = new ArrayList<>();
		for (var clip : track.clips()) keyframes.addAll(cameraKeyframes(clip.events()));
		keyframes.sort(Comparator.comparingDouble(CompiledCameraTrack.CameraEvent::timeSeconds));
		if (keyframes.isEmpty()) return null;
		if (timeSeconds <= keyframes.getFirst().timeSeconds() + 1e-6)
			return sampleKeyframe(keyframes.getFirst(), anchor, fallbackYaw, fallbackPitch);
		if (timeSeconds >= keyframes.getLast().timeSeconds() - 1e-6)
			return sampleKeyframe(keyframes.getLast(), anchor, fallbackYaw, fallbackPitch);
		for (int i = 0; i < keyframes.size() - 1; i++) {
			var a = keyframes.get(i);
			var b = keyframes.get(i + 1);
			if (timeSeconds < a.timeSeconds() - 1e-6 || timeSeconds > b.timeSeconds() + 1e-6) continue;
			double t = (timeSeconds - a.timeSeconds()) / Math.max(1e-6, b.timeSeconds() - a.timeSeconds());
			double weight = "LINEAR".equalsIgnoreCase(stringParam(a.parameters(), "ease", "LINEAR"))
				? t : smoothstep(t);
			return blendKeyframes(a, b, weight, anchor, fallbackYaw, fallbackPitch);
		}
		return sampleKeyframe(keyframes.getFirst(), anchor, fallbackYaw, fallbackPitch);
	}

	private static List<CompiledCameraTrack.CameraEvent> cameraKeyframes(
		List<CompiledCameraTrack.CameraEvent> events) {
		List<CompiledCameraTrack.CameraEvent> result = new ArrayList<>();
		for (var event : events) if (event.type() == EventType.CAMERA_KEYFRAME) result.add(event);
		return result;
	}

	private static CameraSample blendKeyframes(CompiledCameraTrack.CameraEvent a,
		CompiledCameraTrack.CameraEvent b, double weight, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		CameraSample start = sampleKeyframe(a, anchor, fallbackYaw, fallbackPitch);
		CameraSample end = sampleKeyframe(b, anchor, fallbackYaw, fallbackPitch);
		return new CameraSample(start.position().lerp(end.position(), weight),
			lerpAngleDeg(start.yawDeg(), end.yawDeg(), weight),
			(float) lerp(start.pitchDeg(), end.pitchDeg(), weight));
	}

	private static CameraSample sampleKeyframe(CompiledCameraTrack.CameraEvent event,
		Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		Map<String, Object> parameters = event.parameters();
		return new CameraSample(new Vec3d(
			num(parameters, "x", anchor.x), num(parameters, "y", anchor.y), num(parameters, "z", anchor.z)),
			(float) num(parameters, "yawDeg", fallbackYaw),
			(float) num(parameters, "pitchDeg", fallbackPitch));
	}

	public static CameraSample evaluateClip(Clip clip, Timeline timeline, double timeSeconds, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		if (clip == null) return null;
		TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(clip);
		if (seg != null) {
			CameraSegmentKind kind = CameraSegmentKind.fromParam(seg.getParameters().get("kind"));
			CameraSample sample = evaluateSegment(timeline, clip, seg, kind, timeSeconds, anchor, fallbackYaw, fallbackPitch);
			if (sample != null) return sample;
		}
		return evaluateKeyframeHoldInClip(clip, timeSeconds, anchor, fallbackYaw, fallbackPitch);
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
		double easedU = CameraEasing.apply(u, CameraSegmentSemantics.easingFrom(p));
		Map<String, Object> spatial = CameraSegmentSemantics.withFollowDeltaApplied(p);
		double bpm = timeline != null ? timeline.getBpm() : 0;

		CameraSample sample = switch (kind) {
			case PATH -> evaluatePath(clip, timeSeconds, anchor, fallbackYaw, fallbackPitch, p);
			case DOLLY -> evaluateDolly(spatial, easedU, anchor, fallbackYaw, fallbackPitch);
			case ORBIT -> evaluateOrbit(spatial, easedU, anchor, fallbackYaw, fallbackPitch);
			case CRANE -> evaluateCrane(spatial, easedU, anchor, fallbackYaw, fallbackPitch);
			case SHAKE -> evaluateShake(spatial, timeSeconds, anchor, fallbackYaw, fallbackPitch, bpm);
		};
		return finalizeSample(sample, p, kind == CameraSegmentKind.PATH);
	}

	private static CameraSample evaluatePath(Clip clip, double timeSeconds, Vec3d anchor, float fallbackYaw, float fallbackPitch,
		Map<String, Object> segmentParams) {
		List<TimelineEvent> kf = new ArrayList<>();
		for (TimelineEvent e : clip.getEvents()) {
			if (e.getType() == EventType.CAMERA_KEYFRAME) kf.add(e);
		}
		kf.sort(Comparator.comparingDouble(TimelineEvent::getTimeSeconds));
		if (kf.isEmpty()) return finalizeSample(new CameraSample(anchor, fallbackYaw, fallbackPitch), segmentParams, true);
		if (kf.size() == 1) return finalizeSample(sampleKeyframe(kf.getFirst(), anchor, fallbackYaw, fallbackPitch), segmentParams, true);
		if (timeSeconds <= kf.getFirst().getTimeSeconds() + 1e-6) {
			return finalizeSample(sampleKeyframe(kf.getFirst(), anchor, fallbackYaw, fallbackPitch), segmentParams, true);
		}
		if (timeSeconds >= kf.getLast().getTimeSeconds() - 1e-6) {
			return finalizeSample(sampleKeyframe(kf.getLast(), anchor, fallbackYaw, fallbackPitch), segmentParams, true);
		}
		for (int i = 0; i < kf.size() - 1; i++) {
			TimelineEvent a = kf.get(i);
			TimelineEvent b = kf.get(i + 1);
			double ta = a.getTimeSeconds();
			double tb = b.getTimeSeconds();
			if (timeSeconds < ta - 1e-6 || timeSeconds > tb + 1e-6) continue;
			double span = Math.max(1e-6, tb - ta);
			double t = (timeSeconds - ta) / span;
			t = Math.max(0.0, Math.min(1.0, t));
			String ease = stringParam(a.getParameters(), "ease", "SMOOTH");
			double wt = CameraEasing.apply(t, ease);
			return finalizeSample(blendKeyframes(a, b, wt, anchor, fallbackYaw, fallbackPitch), segmentParams, true);
		}
		return finalizeSample(sampleKeyframe(kf.getFirst(), anchor, fallbackYaw, fallbackPitch), segmentParams, true);
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
			if (timeSeconds < ta - 1e-6 || timeSeconds > tb + 1e-6) continue;
			double span = Math.max(1e-6, tb - ta);
			double t = (timeSeconds - ta) / span;
			t = Math.max(0.0, Math.min(1.0, t));
			String ease = stringParam(a.getParameters(), "ease", "LINEAR");
			double wt = "LINEAR".equalsIgnoreCase(ease) ? t : smoothstep(t);
			return blendKeyframes(a, b, wt, anchor, fallbackYaw, fallbackPitch);
		}
		return sampleKeyframe(kf.getFirst(), anchor, fallbackYaw, fallbackPitch);
	}

	private static CameraSample evaluateDolly(Map<String, Object> p, double u, Vec3d anchor, float yawDeg, float pitchDeg) {
		double w = u;
		if (p != null && p.containsKey("endX")) {
			double sx = num(p, "startX", anchor.x);
			double sy = num(p, "startY", anchor.y);
			double sz = num(p, "startZ", anchor.z);
			double ex = num(p, "endX", sx);
			double ey = num(p, "endY", sy);
			double ez = num(p, "endZ", sz);
			Vec3d start = new Vec3d(sx, sy, sz);
			Vec3d end = new Vec3d(ex, ey, ez);
			Vec3d pos = start.lerp(end, w);
			Vec3d d = end.subtract(start);
			double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
			if (horiz < 1e-4 && Math.abs(d.y) < 1e-4) {
				// 起终点重合：fallback 到参数中指定的朝向或调用者传入的默认值
				float outYaw = (float) num(p, "baseYawDeg", yawDeg);
				float outPitch = (float) num(p, "basePitchDeg", pitchDeg);
				return new CameraSample(pos, outYaw, outPitch);
			}
			float outYaw = horiz > 1e-4
				? (float) Math.toDegrees(Math.atan2(-d.x, d.z))
				: (float) num(p, "baseYawDeg", yawDeg);
			float outPitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.max(1e-6, horiz)));
			return new CameraSample(pos, outYaw, outPitch);
		}
		double ax = num(p, "anchorX", anchor.x);
		double ay = num(p, "anchorY", anchor.y);
		double az = num(p, "anchorZ", anchor.z);
		double baseYaw = num(p, "baseYawDeg", yawDeg);
		Vec3d forward = horizontalForward((float) baseYaw);
		double d0 = num(p, "distance0", 2.0);
		double d1 = num(p, "distance1", 8.0);
		double d = lerp(d0, d1, w);
		Vec3d pos = new Vec3d(ax, ay, az).add(forward.multiply(d));
		return new CameraSample(pos, (float) baseYaw, pitchDeg);
	}

	private static CameraSample evaluateOrbit(Map<String, Object> p, double u, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		double w = u;
		double ax = num(p, "anchorX", anchor.x);
		double ay = num(p, "anchorY", anchor.y);
		double az = num(p, "anchorZ", anchor.z);
		double tx = num(p, "targetX", ax);
		double ty = num(p, "targetY", ay);
		double tz = num(p, "targetZ", az);
		double radius = num(p, "radius", 10.0);
		double height = num(p, "height", 4.0);
		double y0 = num(p, "yawStartDeg", 0.0);
		double y1 = num(p, "yawEndDeg", 270.0);
		double orbitYawDeg = lerp(y0, y1, w);
		double rad = Math.toRadians(-orbitYawDeg);
		double ox = -Math.sin(rad) * radius;
		double oz = Math.cos(rad) * radius;
		Vec3d pos = new Vec3d(tx + ox, ty + height, tz + oz);
		Vec3d toT = new Vec3d(tx, ty, tz).subtract(pos);
		double horiz = Math.sqrt(toT.x * toT.x + toT.z * toT.z);
		float lookYaw = (float) Math.toDegrees(Math.atan2(-toT.x, toT.z));
		float lookPitch = (float) Math.toDegrees(-Math.atan2(toT.y, horiz));
		if (horiz < 1e-4 && Math.abs(toT.y) < 1e-4) {
			return new CameraSample(pos, fallbackYaw, fallbackPitch);
		}
		return new CameraSample(pos, lookYaw, lookPitch);
	}

	private static CameraSample evaluateCrane(Map<String, Object> p, double u, Vec3d anchor, float fallbackYaw, float fallbackPitch) {
		double w = u;
		if (p != null && p.containsKey("endX")) {
			double sx = num(p, "startX", anchor.x);
			double sy = num(p, "startY", anchor.y);
			double sz = num(p, "startZ", anchor.z);
			double ex = num(p, "endX", sx);
			double ey = num(p, "endY", sy);
			double ez = num(p, "endZ", sz);
			Vec3d pos = new Vec3d(sx, sy, sz).lerp(new Vec3d(ex, ey, ez), w);
			double yaw = num(p, "yawDeg", fallbackYaw);
			double pitch = num(p, "pitchDeg", fallbackPitch);
			return new CameraSample(pos, (float) yaw, (float) pitch);
		}
		double ax = num(p, "anchorX", anchor.x);
		double ay = num(p, "anchorY", anchor.y);
		double az = num(p, "anchorZ", anchor.z);
		double yaw = num(p, "yawDeg", fallbackYaw);
		double pitch = num(p, "pitchDeg", fallbackPitch);
		double dist = num(p, "distance", 12.0);
		double h0 = num(p, "height0", 2.0);
		double h1 = num(p, "height1", 10.0);
		double h = lerp(h0, h1, w);
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
		float yaw = lerpAngleDeg(sa.yawDeg(), sb.yawDeg(), w);
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

	/** 最短路径角度差插值，避免跨 ±180° 时绕长弧旋转。 */
	private static float lerpAngleDeg(float a, float b, double t) {
		float diff = ((b - a) % 360f + 540f) % 360f - 180f;
		return a + diff * (float) t;
	}

	private static double num(Map<String, Object> p, String key, double def) {
		if (p == null) return def;
		Object o = p.get(key);
		if (o instanceof Number n) return n.doubleValue();
		if (o != null) {
			try {
				return Double.parseDouble(String.valueOf(o).trim());
			} catch (NumberFormatException e) {
				BeatBlock.LOGGER.debug("Invalid numeric camera parameter '{}', using default {}", key, def, e);
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

	private static CameraSample finalizeSample(
		CameraSample sample,
		Map<String, Object> segmentParams,
		boolean applyFollowOffset
	) {
		if (sample == null) return null;
		CameraSample adjusted = sample;
		if (applyFollowOffset) {
			Optional<Vec3d> followDelta = CameraSegmentSemantics.followDelta(segmentParams);
			if (followDelta.isPresent() && followDelta.get().lengthSquared() > 1e-12) {
				adjusted = new CameraSample(
					sample.position().add(followDelta.get()), sample.yawDeg(), sample.pitchDeg());
			}
		}
		return CameraCollisionAdjuster.adjust(adjusted, CameraSegmentSemantics.collisionPolicyFrom(segmentParams));
	}

	private static CameraSample applyIncomingTransition(
		Track cam,
		Clip active,
		TimelineEvent segment,
		double timeSeconds,
		CameraSample current,
		Timeline timeline,
		Vec3d anchor,
		float fallbackYaw,
		float fallbackPitch
	) {
		return blendIncomingTransition(
			CameraSegmentSemantics.transitionFrom(segment.getParameters()),
			active.getStartTimeSeconds(),
			timeSeconds,
			current,
			findPreviousSample(cam, active.getStartTimeSeconds(), timeline, anchor, fallbackYaw, fallbackPitch)
		);
	}

	private static CameraSample applyIncomingTransition(
		CompiledCameraTrack track,
		CompiledCameraTrack.CameraClip active,
		CompiledCameraTrack.CameraEvent segment,
		double timeSeconds,
		CameraSample current,
		double bpm,
		Vec3d anchor,
		float fallbackYaw,
		float fallbackPitch
	) {
		return blendIncomingTransition(
			CameraSegmentSemantics.transitionFrom(segment.parameters()),
			active.startTimeSeconds(),
			timeSeconds,
			current,
			findPreviousSample(track, active.startTimeSeconds(), bpm, anchor, fallbackYaw, fallbackPitch)
		);
	}

	private static CameraSample blendIncomingTransition(
		CameraShotTransition transition,
		double clipStart,
		double timeSeconds,
		CameraSample current,
		CameraSample previous
	) {
		if (current == null || previous == null || transition == null || transition == CameraShotTransition.CUT) {
			return current;
		}
		double blendSeconds = transition.blendSeconds();
		if (blendSeconds <= 0.0 || timeSeconds <= clipStart || timeSeconds >= clipStart + blendSeconds) {
			return current;
		}
		double weight = CameraEasing.apply((timeSeconds - clipStart) / blendSeconds, CameraShotEasing.SMOOTH);
		return new CameraSample(
			current.position().lerp(previous.position(), 1.0 - weight),
			lerpAngleDeg(current.yawDeg(), previous.yawDeg(), 1.0 - weight),
			(float) lerp(current.pitchDeg(), previous.pitchDeg(), 1.0 - weight)
		);
	}

	private static CameraSample findPreviousSample(
		Track cam,
		double clipStart,
		Timeline timeline,
		Vec3d anchor,
		float fallbackYaw,
		float fallbackPitch
	) {
		double sampleTime = Math.max(0.0, clipStart - 1e-3);
		return evaluate(timeline, sampleTime, anchor, fallbackYaw, fallbackPitch, false);
	}

	private static CameraSample findPreviousSample(
		CompiledCameraTrack track,
		double clipStart,
		double bpm,
		Vec3d anchor,
		float fallbackYaw,
		float fallbackPitch
	) {
		double sampleTime = Math.max(0.0, clipStart - 1e-3);
		return evaluate(track, bpm, sampleTime, anchor, fallbackYaw, fallbackPitch, false);
	}
}
