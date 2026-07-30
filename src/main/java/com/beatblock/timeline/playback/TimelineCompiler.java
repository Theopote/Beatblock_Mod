package com.beatblock.timeline.playback;

import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;

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
			return new CompiledTimelineSnapshot(List.of(), new double[0], 120.0, true, -1);
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
			ReferenceBeatResolver.resolveBeatTimesSeconds(document),
			bpm,
			shouldRestoreWorldMutations(document),
			document.getStageEventsGeneration()
		);
	}

	private static TimelineAnimationEvent copyEvent(TimelineAnimationEvent event) {
		return new TimelineAnimationEvent(
			event.getEventId(),
			event.getTimeSeconds(),
			event.getDurationSeconds(),
			event.getAnimationTypeId(),
			event.getTargetObjectId(),
			event.getEnergy(),
			freezeMap(event.getParameters())
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
		return value;
	}

	private static boolean shouldRestoreWorldMutations(Timeline timeline) {
		Object raw = timeline.getMetadata("timelineActionRollbackMode");
		if (raw == null) return true;
		String mode = String.valueOf(raw).trim().toLowerCase(java.util.Locale.ROOT);
		return !"persistent".equals(mode) && !"performance".equals(mode);
	}
}
