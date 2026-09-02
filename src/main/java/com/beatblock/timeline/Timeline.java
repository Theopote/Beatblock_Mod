package com.beatblock.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.playback.GlobalEventPayloadCodec;
import com.beatblock.client.ClientThreadGuard;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 时间线根对象：名称、时长、轨道列表、元数据。单一时序数据源，替代原 TimelineModel。
 * <p>
 * 线程模型：结构编辑与播放仅在客户端主线程；详见 {@code docs/playback-compiler.md}。
 * {@link ConcurrentHashMap} 仅用于 metadata，以支持异步分析回调写入 BPM 等标量键（非整树线程安全）。
 */
public class Timeline {

	public static final String TRACK_ID_AUDIO = "audio";
	public static final String TRACK_ID_ANIMATION_BLOCK = "animation_block";
	public static final String TRACK_ID_ANIMATION_AUTO = "animation_auto";
	public static final String TRACK_ID_ANIMATION_BLOCK_FEATURE_PREFIX = "animation_block_feature_";
	public static final String TRACK_ID_CAMERA = "camera";
	public static final String TRACK_ID_GLOBAL = "global";
	public static final String TRACK_ID_BUILD_REVERSE = "build_reverse";

	private String name = "";
	private double durationSeconds = 0;
	private final List<Track> tracks = new ArrayList<>();
	private final Map<String, Object> metadata = new ConcurrentHashMap<>();
	private final List<TimelineMarker> markers = new ArrayList<>();
	private final List<TimelineMarker> markerView = Collections.unmodifiableList(markers);
	/**
	 * 统一舞台事件缓存（block / auto / build / feature 轨合并，按时间升序）。
	 * 播放器应优先使用 {@link #getStageEvents()}，避免维护多套扫描循环。
	 */
	private final List<TimelineAnimationEvent> stageEventsCache = new ArrayList<>();
	private final List<TimelineAnimationEvent> blockAnimationCache = new ArrayList<>();
	private final List<TimelineAnimationEvent> autoAnimationCache = new ArrayList<>();
	private final List<TimelineAnimationEvent> buildReverseCache = new ArrayList<>();
	/** 按 trackId 缓存的事件列表（与 stage 缓存同生命周期，避免渲染每帧临时构建）。 */
	private final Map<String, List<TimelineAnimationEvent>> animationEventsByTrackId = new HashMap<>();
	private final List<TimelineAnimationEvent> stageEventsCacheView = Collections.unmodifiableList(stageEventsCache);
	private final List<TimelineAnimationEvent> blockAnimationCacheView = Collections.unmodifiableList(blockAnimationCache);
	private final List<TimelineAnimationEvent> autoAnimationCacheView = Collections.unmodifiableList(autoAnimationCache);
	private final List<TimelineAnimationEvent> buildReverseCacheView = Collections.unmodifiableList(buildReverseCache);
	private volatile boolean animationCachesDirty = true;
	/** 每次舞台事件缓存重建递增，供播放游标在编辑后回退重扫。 */
	private int stageEventsGeneration;

	private static void requireClientThread() {
		ClientThreadGuard.assertClientThread();
	}

	public @NonNull String getName() { return name; }
	public void setName(@Nullable String name) {
		requireClientThread();
		this.name = name != null ? name : "";
	}
	public double getDurationSeconds() { return durationSeconds; }
	public void setDurationSeconds(double durationSeconds) {
		requireClientThread();
		this.durationSeconds = Math.max(0, durationSeconds);
	}
	public @NonNull List<Track> getTracks() { return Collections.unmodifiableList(tracks); }
	public void addTrack(@Nullable Track track) {
		requireClientThread();
		if (track != null) {
			tracks.add(track);
			markAnimationEventsDirty(track.getId());
		}
	}
	public boolean removeTrack(@Nullable String trackId) {
		requireClientThread();
		boolean removed = tracks.removeIf(t -> trackId != null && trackId.equals(t.getId()));
		if (removed) markAnimationEventsDirty(trackId);
		return removed;
	}
	public @Nullable Track getTrack(@Nullable String trackId) {
		for (Track t : tracks) if (trackId != null && trackId.equals(t.getId())) return t;
		return null;
	}
	public @Nullable Track getTrackByType(@NonNull TrackType type) {
		for (Track t : tracks) if (t.getType() == type) return t;
		return null;
	}
	public @NonNull Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
	public void setMetadata(@Nullable String key, @Nullable Object value) {
		if (key == null) return;
		if (value == null) {
			metadata.remove(key);
			return;
		}
		metadata.put(key, value);
	}
	public @Nullable Object getMetadata(@Nullable String key) {
		return key != null ? metadata.get(key) : null;
	}
	public @NonNull List<TimelineMarker> getMarkers() { return markerView; }
	/** BPM（由音频分析填入 metadata["bpm"]），未设置时返回 0。 */
	public double getBpm() {
		Object v = metadata.get("bpm");
		if (v instanceof Number) return ((Number) v).doubleValue();
		return 0;
	}

	public void addMarker(@Nullable TimelineMarker marker) {
		requireClientThread();
		if (marker == null) return;
		markers.add(marker);
		markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
	}

	public void clearMarkers() {
		requireClientThread();
		markers.clear();
	}

	public void setMarkers(@Nullable List<TimelineMarker> newMarkers) {
		requireClientThread();
		markers.clear();
		if (newMarkers != null) {
			markers.addAll(newMarkers);
			markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		}
	}

	public int findMarkerIndexById(String markerId) {
		if (markerId == null || markerId.isBlank()) return -1;
		for (int i = 0; i < markers.size(); i++) {
			TimelineMarker marker = markers.get(i);
			if (markerId.equals(marker.getId())) return i;
		}
		return -1;
	}

	public boolean removeMarker(int index) {
		requireClientThread();
		if (index < 0 || index >= markers.size()) return false;
		markers.remove(index);
		return true;
	}

	public boolean removeMarker(String markerId) {
		int index = findMarkerIndexById(markerId);
		return removeMarker(index);
	}

	public boolean updateMarker(int index, double timeSeconds, String name) {
		requireClientThread();
		if (index < 0 || index >= markers.size()) return false;
		TimelineMarker prev = markers.get(index);
		markers.set(index, new TimelineMarker(prev.getId(), timeSeconds, name, prev.getType()));
		markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return true;
	}

	public boolean updateMarker(int index, double timeSeconds, String name, MarkerType type) {
		requireClientThread();
		if (index < 0 || index >= markers.size()) return false;
		TimelineMarker prev = markers.get(index);
		markers.set(index, new TimelineMarker(prev.getId(), timeSeconds, name, type));
		markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return true;
	}

	public boolean updateMarker(String markerId, double timeSeconds, String name) {
		int index = findMarkerIndexById(markerId);
		if (index < 0) return false;
		TimelineMarker prev = markers.get(index);
		markers.set(index, new TimelineMarker(prev.getId(), timeSeconds, name, prev.getType()));
		markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return true;
	}

	public boolean updateMarker(String markerId, double timeSeconds, String name, MarkerType type) {
		int index = findMarkerIndexById(markerId);
		if (index < 0) return false;
		TimelineMarker prev = markers.get(index);
		markers.set(index, new TimelineMarker(prev.getId(), timeSeconds, name, type));
		markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return true;
	}

	// ----- 便捷 API（兼容原 TimelineModel 读写） -----

	public @Nullable WaveformData getWaveform() {
		AudioTrackData ad = getAudioTrackData();
		return ad != null ? ad.getWaveform() : null;
	}
	public void setWaveform(@Nullable WaveformData waveform) {
		requireClientThread();
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.setWaveform(waveform);
	}

	// ── 特征轨道 API（kick / snare / hihat 等开放键） ─────────────────

	/**
	 * 向命名特征轨道追加事件（首次写入时自动创建轨道）。
	 *
	 * @param key   轨道键，如 "kick"、"snare"、"hihat"
	 * @param event 特征事件
	 */
	public void addFeatureEvent(String key, FeatureEvent event) {
		requireClientThread();
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.addFeatureEvent(key, event);
	}

	/**
	 * 向命名特征轨道追加事件，并指定显示名称（首次创建时生效）。
	 */
	public void addFeatureEvent(String key, String label, FeatureEvent event) {
		requireClientThread();
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.addFeatureEvent(key, label, event);
	}

	/** 获取指定 key 的特征轨道事件列表，不存在返回空列表。 */
	public List<FeatureEvent> getFeatureEvents(String key) {
		AudioTrackData ad = getAudioTrackData();
		if (ad == null) return List.of();
		FeatureTrack ft = ad.getFeatureTrack(key);
		return ft != null ? ft.getEvents() : List.of();
	}

	/** 获取所有命名特征轨道（保持插入顺序）。 */
	public java.util.Map<String, FeatureTrack> getFeatureTracks() {
		AudioTrackData ad = getAudioTrackData();
		if (ad == null) return Map.of();
		return ad.getFeatureTracks();
	}

	/** 是否包含任何命名特征轨道数据。 */
	public boolean hasFeatureTracks() {
		AudioTrackData ad = getAudioTrackData();
		return ad != null && ad.hasFeatureTracks();
	}

	/** 清空所有命名特征轨道（不清除遗留频段）。 */
	public void clearFeatureTracks() {
		requireClientThread();
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.clearFeatureTracks();
	}

	// ── 茎波形委托（Demucs 模式）──────────────────────────────────────────

	public void setStemWaveform(@Nullable String stemKey, @Nullable WaveformData data) {
		requireClientThread();
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.setStemWaveform(stemKey, data);
	}

	public @Nullable WaveformData getStemWaveform(@Nullable String stemKey) {
		AudioTrackData ad = getAudioTrackData();
		return ad != null ? ad.getStemWaveform(stemKey) : null;
	}

	public java.util.Set<String> getStemWaveformKeys() {
		AudioTrackData ad = getAudioTrackData();
		return ad != null ? ad.getStemWaveformKeys() : java.util.Set.of();
	}

	public boolean hasStemWaveforms() {
		AudioTrackData ad = getAudioTrackData();
		return ad != null && ad.hasStemWaveforms();
	}

	/**
	 * 全部舞台动画事件（含手动方块轨、特征子轨、自动映射轨、建造还原/图层轨），按时间升序。
	 * 播放调度的权威列表。
	 */
	public @NonNull List<TimelineAnimationEvent> getStageEvents() {
		rebuildAnimationEventCachesIfNeeded();
		return stageEventsCacheView;
	}

	/**
	 * 舞台事件缓存世代：每次脏重建后递增。播放器可据此在时间轴被编辑后把游标回退到 0 重扫。
	 */
	public int getStageEventsGeneration() {
		rebuildAnimationEventCachesIfNeeded();
		return stageEventsGeneration;
	}

	/** 手动方块动画 + 特征子轨事件（UI / 兼容 API）。 */
	public List<TimelineAnimationEvent> getBlockAnimationEvents() {
		rebuildAnimationEventCachesIfNeeded();
		return blockAnimationCacheView;
	}
	public void addBlockAnimationEvent(TimelineAnimationEvent e) { addAnimationEvent(TRACK_ID_ANIMATION_BLOCK, e); }
	public void clearBlockAnimationEvents() { clearClips(TRACK_ID_ANIMATION_BLOCK); }

	/** 自动映射轨事件（UI / 兼容 API）。 */
	public List<TimelineAnimationEvent> getAutoAnimationEvents() {
		rebuildAnimationEventCachesIfNeeded();
		return autoAnimationCacheView;
	}
	public void addAutoAnimationEvent(TimelineAnimationEvent e) { addAnimationEvent(TRACK_ID_ANIMATION_AUTO, e); }
	public void clearAutoAnimationEvents() { clearClips(TRACK_ID_ANIMATION_AUTO); }

	/** 建造还原 / 建造图层轨事件（UI / 兼容 API）。 */
	public List<TimelineAnimationEvent> getBuildReverseEvents() {
		rebuildAnimationEventCachesIfNeeded();
		return buildReverseCacheView;
	}
	public void clearBuildReverseEvents() { clearClips(TRACK_ID_BUILD_REVERSE); }

	public static String blockAnimationFeatureTrackId(String featureKey) {
		String safe = featureKey == null ? "unknown" : featureKey.trim();
		if (safe.isEmpty()) safe = "unknown";
		return TRACK_ID_ANIMATION_BLOCK_FEATURE_PREFIX + safe;
	}

	public static boolean isBlockAnimationFeatureTrackId(String trackId) {
		return trackId != null && trackId.startsWith(TRACK_ID_ANIMATION_BLOCK_FEATURE_PREFIX);
	}

	public static boolean isAnimationEventsTrackId(String trackId) {
		return TRACK_ID_ANIMATION_BLOCK.equals(trackId)
			|| TRACK_ID_ANIMATION_AUTO.equals(trackId)
			|| BuildLayerTrackSupport.isBuildLayerTrackId(trackId)
			|| isBlockAnimationFeatureTrackId(trackId);
	}

	public static String blockAnimationFeatureKeyFromTrackId(String trackId) {
		if (!isBlockAnimationFeatureTrackId(trackId)) return "";
		return trackId.substring(TRACK_ID_ANIMATION_BLOCK_FEATURE_PREFIX.length());
	}

	/**
	 * 指定轨上的动画事件（只读缓存视图）。缓存脏时与 {@link #getStageEvents()} 一并重建。
	 */
	public List<TimelineAnimationEvent> getAnimationEvents(String trackId) {
		if (trackId == null || trackId.isBlank()) {
			return List.of();
		}
		rebuildAnimationEventCachesIfNeeded();
		List<TimelineAnimationEvent> cached = animationEventsByTrackId.get(trackId);
		return cached != null ? cached : List.of();
	}

	public void addAnimationEvent(String trackId, TimelineAnimationEvent e) {
		requireClientThread();
		addAnimationEventInternal(trackId, e);
	}

	public void clearAnimationTrack(String trackId) {
		clearClips(trackId);
	}

	/**
	 * 按 {@link TimelineEventOrigin} 聚合 block + auto 侧动画事件（单次缓存重建）。
	 * 不含建造还原轨（与历史语义一致）。
	 */
	public List<TimelineAnimationEvent> getAnimationEventsByOrigin(TimelineEventOrigin origin) {
		rebuildAnimationEventCachesIfNeeded();
		TimelineEventOrigin filter = origin != null ? origin : TimelineEventOrigin.MANUAL;
		List<TimelineAnimationEvent> result = new ArrayList<>();
		for (TimelineAnimationEvent event : blockAnimationCache) {
			if (event.getEventOrigin() == filter) result.add(event);
		}
		for (TimelineAnimationEvent event : autoAnimationCache) {
			if (event.getEventOrigin() == filter) result.add(event);
		}
		result.sort(Comparator.comparingDouble(TimelineAnimationEvent::getTimeSeconds));
		return Collections.unmodifiableList(result);
	}

	public void markAnimationEventsDirty(@Nullable String trackId) {
		requireClientThread();
		animationCachesDirty = true;
	}

	public void markAnimationEventsDirty() {
		requireClientThread();
		animationCachesDirty = true;
	}

	/**
	 * 舞台事件所属缓存桶：决定兼容 API 的过滤视图，以及统一列表的构成。
	 */
	private enum StageEventBucket {
		BLOCK,
		AUTO,
		BUILD
	}

	private static @Nullable StageEventBucket bucketForTrackId(@Nullable String trackId) {
		if (trackId == null || trackId.isBlank()) return null;
		if (TRACK_ID_ANIMATION_AUTO.equals(trackId)) return StageEventBucket.AUTO;
		if (BuildLayerTrackSupport.isBuildLayerTrackId(trackId)) return StageEventBucket.BUILD;
		if (TRACK_ID_ANIMATION_BLOCK.equals(trackId) || isBlockAnimationFeatureTrackId(trackId)) {
			return StageEventBucket.BLOCK;
		}
		return null;
	}

	private void rebuildAnimationEventCachesIfNeeded() {
		if (!animationCachesDirty) return;
		stageEventsCache.clear();
		blockAnimationCache.clear();
		autoAnimationCache.clear();
		buildReverseCache.clear();
		animationEventsByTrackId.clear();

		List<TimelineAnimationEvent> trackBuffer = new ArrayList<>();
		for (Track track : tracks) {
			StageEventBucket bucket = bucketForTrackId(track.getId());
			if (bucket == null) continue;
			rebuildAnimationCache(track.getId(), trackBuffer);
			// 空列表也缓存，避免调用方反复扫轨
			List<TimelineAnimationEvent> perTrack = List.copyOf(trackBuffer);
			animationEventsByTrackId.put(track.getId(), perTrack);
			if (trackBuffer.isEmpty()) continue;
			switch (bucket) {
				case BLOCK -> blockAnimationCache.addAll(trackBuffer);
				case AUTO -> autoAnimationCache.addAll(trackBuffer);
				case BUILD -> buildReverseCache.addAll(trackBuffer);
			}
			stageEventsCache.addAll(trackBuffer);
		}

		Comparator<TimelineAnimationEvent> byTime = Comparator.comparingDouble(TimelineAnimationEvent::getTimeSeconds);
		blockAnimationCache.sort(byTime);
		autoAnimationCache.sort(byTime);
		buildReverseCache.sort(byTime);
		stageEventsCache.sort(byTime);
		stageEventsGeneration++;
		animationCachesDirty = false;
	}

	public List<CameraKeyframe> getCameraKeyframes() {
		List<CameraKeyframe> out = new ArrayList<>();
		Track t = getTrack(TRACK_ID_CAMERA);
		if (t == null) return out;
		for (Clip c : t.getClips())
			for (TimelineEvent e : c.getEvents())
				if (e.getType() == EventType.CAMERA_KEYFRAME)
					out.add(new CameraKeyframe(e.getTimeSeconds()));
		out.sort(Comparator.comparingDouble(CameraKeyframe::getTimeSeconds));
		return out;
	}
	public void addCameraKeyframe(CameraKeyframe k) {
		requireClientThread();
		if (k == null) return;
		Track t = getTrack(TRACK_ID_CAMERA);
		if (t == null) return;
		Clip clip = TimelineOperations.addClip(t, k.getTimeSeconds(), k.getTimeSeconds() + 0.1);
		if (clip != null) {
			TimelineOperations.addEvent(
				clip,
				k.getTimeSeconds(),
				EventType.CAMERA_KEYFRAME,
				TimelineEventOriginSupport.manualOrigin(Map.of())
			);
		}
	}
	public void clearCameraKeyframes() { clearClips(TRACK_ID_CAMERA); }
	public void clearAutoGeneratedCameraClips() { clearAutoGeneratedClips(TRACK_ID_CAMERA); }

	public List<GlobalEvent> getGlobalEvents() {
		List<GlobalEvent> out = new ArrayList<>();
		Track t = getTrack(TRACK_ID_GLOBAL);
		if (t == null) return out;
		for (Clip c : t.getClips())
			for (TimelineEvent e : c.getEvents()) {
				if (e.getType() != EventType.GLOBAL) continue;
				Map<String, Object> p = e.getParameters();
				String typeStr = (String) p.getOrDefault("type", "SPECIAL");
				String name = (String) p.getOrDefault("name", "");
				try {
					out.add(new GlobalEvent(e.getTimeSeconds(), GlobalEventType.valueOf(typeStr), name));
				} catch (IllegalArgumentException ex) {
					com.beatblock.BeatBlock.LOGGER.debug("Unknown global event type '{}', using SPECIAL", typeStr, ex);
					out.add(new GlobalEvent(e.getTimeSeconds(), GlobalEventType.SPECIAL, name));
				}
			}
		out.sort(Comparator.comparingDouble(GlobalEvent::getTimeSeconds));
		return out;
	}
	public void addGlobalEvent(GlobalEvent e) {
		addGlobalEvent(e, TimelineEventOrigin.MANUAL);
	}

	public void addGlobalEvent(GlobalEvent e, TimelineEventOrigin origin) {
		requireClientThread();
		if (e == null) return;
		Track t = getTrack(TRACK_ID_GLOBAL);
		if (t == null) return;
		Clip clip = TimelineOperations.addClip(t, e.getTimeSeconds(), e.getTimeSeconds() + 0.1);
		if (clip != null) {
			Map<String, Object> params = new HashMap<>();
			params.put("type", e.getType().name());
			params.put("name", e.getName());
			TimelineOperations.addEvent(
				clip,
				e.getTimeSeconds(),
				EventType.GLOBAL,
				TimelineEventOriginSupport.withOrigin(params, origin)
			);
		}
	}

	public void addGlobalPayloadEvent(
		double timeSeconds,
		GlobalEventPayload payload,
		TimelineEventOrigin origin
	) {
		addGlobalPayloadEvent(timeSeconds, payload, TimelineGenerationMetadata.fromOrigin(origin));
	}

	public void addGlobalPayloadEvent(
		double timeSeconds,
		GlobalEventPayload payload,
		com.beatblock.timeline.generation.TimelineGenerationMetadata metadata
	) {
		requireClientThread();
		if (payload == null) return;
		Track t = getTrack(TRACK_ID_GLOBAL);
		if (t == null) return;
		Clip clip = TimelineOperations.addClip(t, timeSeconds, timeSeconds + 0.1);
		if (clip != null) {
			Map<String, Object> params = GlobalEventPayloadCodec.encode(payload);
			TimelineOperations.addEvent(
				clip,
				timeSeconds,
				EventType.GLOBAL,
				com.beatblock.timeline.generation.TimelineGenerationMetadataSupport.apply(params, metadata)
			);
		}
	}
	public void clearGlobalEvents() { clearClips(TRACK_ID_GLOBAL); }
	public void clearAutoGeneratedGlobalEvents() { clearAutoGeneratedClips(TRACK_ID_GLOBAL); }

	public void sortAll() {
		requireClientThread();
		// 便捷 getter 已按时间排序返回；若需对 Clip 内 events 原地排序可扩展 Clip.sortEvents()
	}

	private @Nullable AudioTrackData getAudioTrackData() {
		Track t = getTrack(TRACK_ID_AUDIO);
		return t != null ? t.getAudioData() : null;
	}

	private void rebuildAnimationCache(String trackId, List<TimelineAnimationEvent> out) {
		out.clear();
		Track t = getTrack(trackId);
		if (t == null) return;
		for (Clip c : t.getClips())
			for (TimelineEvent e : c.getEvents()) {
				if (e.getType() != EventType.ANIMATION) continue;
				Map<String, Object> p = e.getParameters();
				AnimationEventParams parsed = AnimationEventParams.fromParameterMap(p);
				double dur = p.containsKey("durationSeconds")
					? parsed.durationSeconds()
					: Math.max(0.01, c.getEndTimeSeconds() - c.getStartTimeSeconds());
				String animId = parsed.animationType();
				if (animId.isEmpty()) {
					animId = "bounce";
				}
				out.add(new TimelineAnimationEvent(
					e.getId(),
					e.getTimeSeconds(),
					dur,
					animId,
					parsed.targetObject(),
					parsed.energy(),
					parsed.toParameterMap()
				));
			}
		out.sort(Comparator.comparingDouble(TimelineAnimationEvent::getTimeSeconds));
	}

	private void addAnimationEventInternal(String trackId, TimelineAnimationEvent e) {
		if (e == null) return;
		Track t = getTrack(trackId);
		if (t == null) return;
		Clip clip = TimelineOperations.addClip(t, e.getTimeSeconds(), e.getEndTimeSeconds());
		if (clip == null) return;
		Map<String, Object> params = AnimationEventParams.fromAnimationEvent(e).toParameterMap();
		TimelineOperations.addEvent(clip, e.getTimeSeconds(), EventType.ANIMATION, params);
		markAnimationEventsDirty(trackId);
	}

	private void clearClips(String trackId) {
		Track t = getTrack(trackId);
		if (t == null) return;
		List<String> ids = new ArrayList<>();
		for (Clip c : t.getClips()) ids.add(c.getId());
		for (String id : ids) t.removeClip(id);
		markAnimationEventsDirty(trackId);
	}

	private void clearAutoGeneratedClips(String trackId) {
		applyContentReplacePolicy(trackId, com.beatblock.timeline.generation.ContentReplacePolicy.replaceGenerated());
	}

	public void applyContentReplacePolicy(String trackId, com.beatblock.timeline.generation.ContentReplacePolicy policy) {
		if (policy instanceof com.beatblock.timeline.generation.ContentReplacePolicy.Append) {
			return;
		}
		Track t = getTrack(trackId);
		if (t == null) return;
		List<String> ids = new ArrayList<>();
		for (Clip clip : t.getClips()) {
			if (TimelineClipOrigin.shouldRemove(clip, trackId, policy)) {
				ids.add(clip.getId());
			}
		}
		for (String id : ids) t.removeClip(id);
		markAnimationEventsDirty(trackId);
	}

	public static @NonNull Timeline createDefault() {
		Timeline t = new Timeline();
		t.addTrack(new Track(TRACK_ID_AUDIO, "音频", TrackType.AUDIO));
		t.addTrack(new Track(TRACK_ID_ANIMATION_BLOCK, "方块动画", TrackType.ANIMATION));
		t.addTrack(new Track(TRACK_ID_ANIMATION_AUTO, "自动动画", TrackType.ANIMATION));
		BuildLayerTrackSupport.ensureDefaultTrack(t);
		t.addTrack(new Track(TRACK_ID_CAMERA, "摄像机", TrackType.CAMERA));
		t.addTrack(new Track(TRACK_ID_GLOBAL, "全局事件", TrackType.EVENT));
		return t;
	}
}
