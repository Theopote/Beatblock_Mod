package com.beatblock.timeline.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时间线根对象：名称、时长、轨道列表、元数据（BPM、songName 等）。
 * 与 UI 分离，为编辑器标准结构。
 */
public class Timeline {

	private String name = "";
	private double durationSeconds = 0;
	private final List<Track> tracks = new ArrayList<>();
	private final Map<String, Object> metadata = new ConcurrentHashMap<>();

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name != null ? name : "";
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(double durationSeconds) {
		this.durationSeconds = Math.max(0, durationSeconds);
	}

	public List<Track> getTracks() {
		return Collections.unmodifiableList(tracks);
	}

	public void addTrack(Track track) {
		if (track != null) tracks.add(track);
	}

	public boolean removeTrack(String trackId) {
		return tracks.removeIf(t -> trackId != null && trackId.equals(t.getId()));
	}

	public Track getTrack(String trackId) {
		for (Track t : tracks) {
			if (trackId != null && trackId.equals(t.getId())) return t;
		}
		return null;
	}

	public Track getTrackByType(TrackType type) {
		for (Track t : tracks) {
			if (t.getType() == type) return t;
		}
		return null;
	}

	public Map<String, Object> getMetadata() {
		return Collections.unmodifiableMap(metadata);
	}

	public void setMetadata(String key, Object value) {
		if (key != null) metadata.put(key, value);
	}

	public Object getMetadata(String key) {
		return metadata.get(key);
	}

	/** 创建带标准五轨的空时间线（音频 / 方块动画 / 自动动画 / 摄像机 / 全局事件），便于与 TimelineModel 同步。 */
	public static Timeline createDefault() {
		Timeline t = new Timeline();
		t.addTrack(new Track(TimelineModelSync.TRACK_ID_AUDIO, "音频", TrackType.AUDIO));
		t.addTrack(new Track(TimelineModelSync.TRACK_ID_ANIMATION_BLOCK, "方块动画", TrackType.ANIMATION));
		t.addTrack(new Track(TimelineModelSync.TRACK_ID_ANIMATION_AUTO, "自动动画", TrackType.ANIMATION));
		t.addTrack(new Track(TimelineModelSync.TRACK_ID_CAMERA, "摄像机", TrackType.CAMERA));
		t.addTrack(new Track(TimelineModelSync.TRACK_ID_GLOBAL, "全局事件", TrackType.EVENT));
		return t;
	}
}
