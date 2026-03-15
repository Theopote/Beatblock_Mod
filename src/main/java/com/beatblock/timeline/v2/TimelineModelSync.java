package com.beatblock.timeline.v2;

import com.beatblock.timeline.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Timeline（v2 分层结构）与 TimelineModel（当前平铺列表）的双向同步，保证渐进迁移时现有 UI 与逻辑仍可用。
 */
public final class TimelineModelSync {

	public static final String TRACK_ID_AUDIO = "audio";
	public static final String TRACK_ID_ANIMATION_BLOCK = "animation_block";
	public static final String TRACK_ID_ANIMATION_AUTO = "animation_auto";
	public static final String TRACK_ID_CAMERA = "camera";
	public static final String TRACK_ID_GLOBAL = "global";

	/**
	 * 从 Timeline（v2）同步到 TimelineModel（填充 flat 列表），供当前 TimelinePanel / 播放器读取。
	 */
	public static void copyToTimelineModel(Timeline from, TimelineModel to) {
		if (from == null || to == null) return;
		to.setDurationSeconds(from.getDurationSeconds());

		Track audioTrack = from.getTrack(TRACK_ID_AUDIO);
		if (audioTrack != null && audioTrack.getAudioData() != null) {
			AudioTrackData ad = audioTrack.getAudioData();
			to.setWaveform(ad.getWaveform());
			to.clearFrequencyEvents();
			for (FrequencyEvent e : ad.getLowBand()) to.addFrequencyEvent(e);
			for (FrequencyEvent e : ad.getMidBand()) to.addFrequencyEvent(e);
			for (FrequencyEvent e : ad.getHighBand()) to.addFrequencyEvent(e);
		}

		to.clearBlockAnimationEvents();
		to.clearAutoAnimationEvents();
		Track blockTrack = from.getTrack(TRACK_ID_ANIMATION_BLOCK);
		if (blockTrack != null) {
			for (Clip c : blockTrack.getClips()) {
				for (TimelineEvent e : c.getEvents()) {
					if (e.getType() != EventType.ANIMATION) continue;
					to.addBlockAnimationEvent(toAnimationEvent(e, c));
				}
			}
		}
		Track autoTrack = from.getTrack(TRACK_ID_ANIMATION_AUTO);
		if (autoTrack != null) {
			for (Clip c : autoTrack.getClips()) {
				for (TimelineEvent e : c.getEvents()) {
					if (e.getType() != EventType.ANIMATION) continue;
					to.addAutoAnimationEvent(toAnimationEvent(e, c));
				}
			}
		}

		to.clearCameraKeyframes();
		Track cameraTrack = from.getTrack(TRACK_ID_CAMERA);
		if (cameraTrack != null) {
			for (Clip c : cameraTrack.getClips()) {
				for (TimelineEvent e : c.getEvents()) {
					if (e.getType() == EventType.CAMERA_KEYFRAME) {
						to.addCameraKeyframe(new CameraKeyframe(e.getTimeSeconds()));
					}
				}
			}
		}
		to.clearGlobalEvents();
		Track globalTrack = from.getTrack(TRACK_ID_GLOBAL);
		if (globalTrack != null) {
			for (Clip c : globalTrack.getClips()) {
				for (TimelineEvent e : c.getEvents()) {
					if (e.getType() == EventType.GLOBAL) {
						Map<String, Object> p = e.getParameters();
						String typeStr = (String) p.getOrDefault("type", "SPECIAL");
						String name = (String) p.getOrDefault("name", "");
						try {
							to.addGlobalEvent(new GlobalEvent(e.getTimeSeconds(), GlobalEventType.valueOf(typeStr), name));
						} catch (Exception ignored) {
							to.addGlobalEvent(new GlobalEvent(e.getTimeSeconds(), GlobalEventType.SPECIAL, name));
						}
					}
				}
			}
		}
		to.sortAll();
	}

	/**
	 * 从 TimelineModel（当前数据）同步到 Timeline（v2），用于「导入旧数据」或首次切换到 v2 结构。
	 */
	public static void copyFromTimelineModel(TimelineModel from, Timeline to) {
		if (from == null || to == null) return;
		to.setDurationSeconds(from.getDurationSeconds());

		ensureTrack(to, TRACK_ID_AUDIO, "音频", TrackType.AUDIO);
		Track audioTrack = to.getTrack(TRACK_ID_AUDIO);
		if (audioTrack.getAudioData() != null) {
			audioTrack.getAudioData().setWaveform(from.getWaveform());
			audioTrack.getAudioData().clearAllBands();
			for (FrequencyEvent e : from.getFrequencyEvents()) {
				audioTrack.getAudioData().addFrequencyEvent(e);
			}
		}

		ensureTrack(to, TRACK_ID_ANIMATION_BLOCK, "方块动画", TrackType.ANIMATION);
		ensureTrack(to, TRACK_ID_ANIMATION_AUTO, "自动动画", TrackType.ANIMATION);
		Track blockTrack = to.getTrack(TRACK_ID_ANIMATION_BLOCK);
		Track autoTrack = to.getTrack(TRACK_ID_ANIMATION_AUTO);
		for (TimelineAnimationEvent e : from.getBlockAnimationEvents()) {
			Clip clip = TimelineOperations.addClip(blockTrack, e.getTimeSeconds(), e.getEndTimeSeconds());
			if (clip != null) fromAnimationEvent(e, clip);
		}
		for (TimelineAnimationEvent e : from.getAutoAnimationEvents()) {
			Clip clip = TimelineOperations.addClip(autoTrack, e.getTimeSeconds(), e.getEndTimeSeconds());
			if (clip != null) fromAnimationEvent(e, clip);
		}

		ensureTrack(to, TRACK_ID_CAMERA, "摄像机", TrackType.CAMERA);
		Track cameraTrack = to.getTrack(TRACK_ID_CAMERA);
		for (CameraKeyframe k : from.getCameraKeyframes()) {
			Clip clip = TimelineOperations.addClip(cameraTrack, k.getTimeSeconds(), k.getTimeSeconds() + 0.1);
			if (clip != null) {
				Map<String, Object> params = new HashMap<>();
				TimelineOperations.addEvent(clip, k.getTimeSeconds(), EventType.CAMERA_KEYFRAME, params);
			}
		}

		ensureTrack(to, TRACK_ID_GLOBAL, "全局事件", TrackType.EVENT);
		Track globalTrack = to.getTrack(TRACK_ID_GLOBAL);
		for (GlobalEvent e : from.getGlobalEvents()) {
			Clip clip = TimelineOperations.addClip(globalTrack, e.getTimeSeconds(), e.getTimeSeconds() + 0.1);
			if (clip != null) {
				Map<String, Object> params = new HashMap<>();
				params.put("type", e.getType().name());
				params.put("name", e.getName());
				TimelineOperations.addEvent(clip, e.getTimeSeconds(), EventType.GLOBAL, params);
			}
		}
	}

	private static void ensureTrack(Timeline timeline, String id, String name, TrackType type) {
		if (timeline.getTrack(id) == null) {
			Track t = new Track(id, name, type);
			timeline.addTrack(t);
		}
	}

	private static TimelineAnimationEvent toAnimationEvent(TimelineEvent e, Clip clip) {
		Map<String, Object> p = e.getParameters();
		Object durObj = p.get("durationSeconds");
		double dur = durObj instanceof Number ? ((Number) durObj).doubleValue() : Math.max(0.01, clip.getEndTimeSeconds() - clip.getStartTimeSeconds());
		String animId = (String) p.getOrDefault("animationType", "bounce");
		String target = (String) p.getOrDefault("targetObject", "");
		float energy = p.get("energy") instanceof Number ? ((Number) p.get("energy")).floatValue() : 1f;
		return new TimelineAnimationEvent(e.getTimeSeconds(), dur, animId, target, energy, new HashMap<>(p));
	}

	private static void fromAnimationEvent(TimelineAnimationEvent e, Clip clip) {
		Map<String, Object> params = new HashMap<>();
		params.put("animationType", e.getAnimationTypeId());
		params.put("targetObject", e.getTargetObjectId());
		params.put("energy", e.getEnergy());
		params.put("durationSeconds", e.getDurationSeconds());
		params.putAll(e.getParameters());
		TimelineOperations.addEvent(clip, e.getTimeSeconds(), EventType.ANIMATION, params);
	}
}
