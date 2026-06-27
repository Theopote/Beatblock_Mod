package com.beatblock.timeline.editing;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.camera.CameraPathMetadata;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 时间线事件编辑快照：主事件参数、片段时间、片段内其他事件时间、Timeline 元数据。
 */
public record AnimationEventSnapshot(
	double timeSeconds,
	Map<String, Object> parameters,
	double clipStartSeconds,
	double clipEndSeconds,
	Map<String, Double> clipEventTimesById,
	Map<String, String> timelineMetadata,
	double timelineDurationSeconds
) {

	public AnimationEventSnapshot {
		parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
		clipEventTimesById = clipEventTimesById != null ? Map.copyOf(clipEventTimesById) : Map.of();
		timelineMetadata = timelineMetadata != null ? Map.copyOf(timelineMetadata) : Map.of();
	}

	public AnimationEventSnapshot(
		double timeSeconds,
		Map<String, Object> parameters,
		double clipStartSeconds,
		double clipEndSeconds
	) {
		this(timeSeconds, parameters, clipStartSeconds, clipEndSeconds, Map.of(), Map.of(), 0.0);
	}

	public static @NonNull AnimationEventSnapshot capture(@Nullable TimelineEvent event, @NonNull Clip clip) {
		return capture(event, clip, null, null);
	}

	public static @NonNull AnimationEventSnapshot capture(
		@Nullable TimelineEvent event,
		@NonNull Clip clip,
		@Nullable Timeline timeline,
		@Nullable String clipId
	) {
		Map<String, Double> eventTimes = new HashMap<>();
		for (TimelineEvent clipEvent : clip.getEvents()) {
			eventTimes.put(clipEvent.getId(), clipEvent.getTimeSeconds());
		}
		Map<String, String> metadata = Map.of();
		double duration = timeline != null ? timeline.getDurationSeconds() : 0.0;
		if (timeline != null && clipId != null && !clipId.isBlank()) {
			metadata = Map.of(
				CameraPathMetadata.metadataKey(clipId),
				CameraPathMetadata.metadataValue(CameraPathMetadata.isPathVisible(timeline, clipId))
			);
		}
		if (event == null) {
			return captureClipOnly(clip, timeline, clipId);
		}
		return new AnimationEventSnapshot(
			event.getTimeSeconds(),
			new HashMap<>(event.getParameters()),
			clip.getStartTimeSeconds(),
			clip.getEndTimeSeconds(),
			eventTimes,
			metadata,
			duration
		);
	}

	public static @NonNull AnimationEventSnapshot captureClipOnly(
		@NonNull Clip clip,
		@Nullable Timeline timeline,
		@Nullable String clipId
	) {
		TimelineEvent head = clip.getEvents().isEmpty() ? null : clip.getEvents().get(0);
		if (head == null) {
			return new AnimationEventSnapshot(
				0.0, Map.of(),
				clip.getStartTimeSeconds(), clip.getEndTimeSeconds(),
				captureEventTimes(clip),
				capturePathMetadata(timeline, clipId),
				timeline != null ? timeline.getDurationSeconds() : 0.0
			);
		}
		return capture(head, clip, timeline, clipId);
	}

	public void applyTo(@Nullable TimelineEvent event, @NonNull Clip clip, @Nullable Timeline timeline) {
		if (!parameters.isEmpty() && event != null) {
			for (String key : new ArrayList<>(event.getParameters().keySet())) {
				event.removeParameter(key);
			}
			for (Map.Entry<String, Object> entry : parameters.entrySet()) {
				event.setParameter(entry.getKey(), entry.getValue());
			}
		}
		if (!clipEventTimesById.isEmpty()) {
			for (Map.Entry<String, Double> entry : clipEventTimesById.entrySet()) {
				TimelineEvent clipEvent = clip.getEvent(entry.getKey());
				if (clipEvent != null) {
					clipEvent.setTimeSeconds(entry.getValue());
				}
			}
		} else if (event != null) {
			event.setTimeSeconds(timeSeconds);
		}
		clip.setStartTimeSeconds(clipStartSeconds);
		clip.setEndTimeSeconds(clipEndSeconds);
		if (timeline != null) {
			for (Map.Entry<String, String> entry : timelineMetadata.entrySet()) {
				timeline.setMetadata(entry.getKey(), entry.getValue());
			}
			if (timelineDurationSeconds > 0.0) {
				timeline.setDurationSeconds(timelineDurationSeconds);
			}
		}
	}

	public void applyTo(@Nullable TimelineEvent event, @NonNull Clip clip) {
		applyTo(event, clip, null);
	}

	private static Map<String, Double> captureEventTimes(Clip clip) {
		Map<String, Double> eventTimes = new HashMap<>();
		for (TimelineEvent clipEvent : clip.getEvents()) {
			eventTimes.put(clipEvent.getId(), clipEvent.getTimeSeconds());
		}
		return eventTimes;
	}

	private static Map<String, String> capturePathMetadata(@Nullable Timeline timeline, @Nullable String clipId) {
		if (timeline == null || clipId == null || clipId.isBlank()) {
			return Map.of();
		}
		return Map.of(
			CameraPathMetadata.metadataKey(clipId),
			CameraPathMetadata.metadataValue(CameraPathMetadata.isPathVisible(timeline, clipId))
		);
	}
}
