package com.beatblock.timeline.playback;

import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.payload.StageEventPayload;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObject;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将可编辑 Timeline 编译为一次播放期间稳定的不可变数据。 */
public final class TimelineCompiler {

	private TimelineCompiler() {}

	public static CompiledTimelineSnapshot compile(Timeline document) {
		if (document == null) {
			return new CompiledTimelineSnapshot(List.of(), List.of(), new CompiledCameraTrack(List.of()), new double[0], 120.0, true, -1);
		}
		List<TimelineAnimationEvent> events = new ArrayList<>();
		for (TimelineAnimationEvent event : document.getStageEvents()) {
			events.add(copyEvent(event));
		}
		events.sort(Comparator
			.comparingDouble(TimelineAnimationEvent::getTimeSeconds)
			.thenComparing(TimelineAnimationEvent::getEventId));
		double bpm = document.getBpm() > 0 ? document.getBpm() : 120.0;
		return new CompiledTimelineSnapshot(
			events,
			compileStageEvents(events, null),
			compileCameraTrack(document.getTrack(Timeline.TRACK_ID_CAMERA)),
			ReferenceBeatResolver.resolveBeatTimesSeconds(document),
			bpm,
			shouldRestoreWorldMutations(document),
			document.getStageEventsGeneration()
		);
	}

	public static CompiledTimelineSnapshot compile(Timeline document, BlockAnimationEngine engine) {
		CompiledTimelineSnapshot base = compile(document);
		return new CompiledTimelineSnapshot(
			base.stageEvents(), compileStageEvents(base.stageEvents(), engine), base.cameraTrack(),
			base.referenceBeatTimesSeconds(), base.bpm(), base.restoreWorldMutations(), base.sourceGeneration());
	}

	private static List<CompiledStageEvent> compileStageEvents(
		List<TimelineAnimationEvent> events, @Nullable BlockAnimationEngine engine) {
		List<CompiledStageEvent> compiled = new ArrayList<>(events.size());
		for (TimelineAnimationEvent event : events) {
			var definition = engine != null ? engine.getAnimationLibrary().get(event.getAnimationTypeId()) : null;
			StageObject source = engine != null
				? engine.getStageObjectSystem().get(event.getTargetObjectId()) : null;
			CompiledStageTarget target = null;
			if (source != null) {
				target = new CompiledStageTarget(
					source.getId(),
					source.getName(),
					source.getBlocks(),
					source.getCenter(),
					source.getGroupSpec().getSortingStrategy(),
					source.getGroupSpec().getStaggerDelaySeconds()
				);
			}
			compiled.add(new CompiledStageEvent(event, definition, target));
		}
		return List.copyOf(compiled);
	}

	private static CompiledCameraTrack compileCameraTrack(@Nullable Track track) {
		if (track == null || !track.isEnabled()) return new CompiledCameraTrack(List.of());
		List<CompiledCameraTrack.CameraClip> clips = new ArrayList<>();
		for (var clip : track.getClips()) {
			List<CompiledCameraTrack.CameraEvent> cameraEvents = new ArrayList<>();
			for (var event : clip.getEvents()) {
				cameraEvents.add(new CompiledCameraTrack.CameraEvent(
					event.getId(), event.getTimeSeconds(), event.getType(), freezeMap(event.getParameters())));
			}
			cameraEvents.sort(Comparator.comparingDouble(CompiledCameraTrack.CameraEvent::timeSeconds)
				.thenComparing(CompiledCameraTrack.CameraEvent::id));
			clips.add(new CompiledCameraTrack.CameraClip(
				clip.getStartTimeSeconds(), clip.getEndTimeSeconds(), cameraEvents));
		}
		clips.sort(Comparator.comparingDouble(CompiledCameraTrack.CameraClip::startTimeSeconds));
		return new CompiledCameraTrack(clips);
	}

	private static TimelineAnimationEvent copyEvent(TimelineAnimationEvent event) {
		// 先通过 StageEventPayload 做一次强类型验证，再冻结回参数表。
		// 这样播放快照不再依赖“猜如何深复制任意 Map”，而是只保留已知可序列化的基础类型。
		StageEventPayload payload = event.getPayload();
		return new TimelineAnimationEvent(
			event.getEventId(),
			event.getTimeSeconds(),
			payload.durationSeconds(),
			payload.animationType(),
			payload.targetObject(),
			payload.energy(),
			freezeMap(payload.toParameterMap())
		);
	}

	private static Map<String, Object> freezeMap(Map<String, Object> source) {
		if (source == null || source.isEmpty()) return Map.of();
		Map<String, Object> frozen = new LinkedHashMap<>();
		for (var entry : source.entrySet()) frozen.put(entry.getKey(), freezeValue(entry.getValue()));
		return Map.copyOf(frozen);
	}

	private static Object freezeValue(Object value) {
		if (value == null || value instanceof String || value instanceof Number
			|| value instanceof Boolean || value instanceof Character || value instanceof Enum<?>) return value;
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> frozen = new LinkedHashMap<>();
			for (var entry : map.entrySet()) {
				frozen.put(String.valueOf(entry.getKey()), freezeValue(entry.getValue()));
			}
			return Map.copyOf(frozen);
		}
		if (value instanceof Iterable<?> iterable) {
			List<Object> frozen = new ArrayList<>();
			for (Object item : iterable) frozen.add(freezeValue(item));
			return List.copyOf(frozen);
		}
		if (value.getClass().isArray()) {
			List<Object> frozen = new ArrayList<>(Array.getLength(value));
			for (int i = 0; i < Array.getLength(value); i++) frozen.add(freezeValue(Array.get(value, i)));
			return List.copyOf(frozen);
		}
		throw new TimelineCompilationException(
			"Unsupported mutable parameter type: " + value.getClass().getName());
	}

	private static boolean shouldRestoreWorldMutations(Timeline timeline) {
		Object raw = timeline.getMetadata("timelineActionRollbackMode");
		if (raw == null) return true;
		String mode = String.valueOf(raw).trim().toLowerCase(java.util.Locale.ROOT);
		return !"persistent".equals(mode) && !"performance".equals(mode);
	}
}
