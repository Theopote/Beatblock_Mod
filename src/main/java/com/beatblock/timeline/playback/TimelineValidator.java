package com.beatblock.timeline.playback;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.payload.StageEventPayload;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pre-play validation of an editable {@link Timeline} (Phase A of Timeline Compiler 2.0).
 * <p>
 * Does not mutate the document. Resolves animation library / stage objects when an engine is provided.
 */
public final class TimelineValidator {

	public static final String RULE_DUPLICATE_EVENT_ID = "duplicate_event_id";
	public static final String RULE_INVALID_DURATION = "invalid_duration";
	public static final String RULE_NON_FINITE_EVENT_DURATION = "non_finite_event_duration";
	public static final String RULE_NON_POSITIVE_EVENT_DURATION = "non_positive_event_duration";
	public static final String RULE_MISSING_ANIMATION_PRESET = "missing_animation_preset";
	public static final String RULE_UNSUPPORTED_PAYLOAD = "unsupported_payload";
	public static final String RULE_UNBOUND_TARGET = "unbound_target";
	public static final String RULE_MISSING_STAGE_OBJECT = "missing_stage_object";
	public static final String RULE_EVENT_OUTSIDE_TIMELINE = "event_outside_timeline";
	public static final String RULE_MISSING_AUDIO = "missing_audio_asset";
	public static final String RULE_AUDIO_FILE_MISSING = "audio_file_missing";
	public static final String RULE_MISSING_BUILD_LAYER = "missing_build_layer";
	public static final String RULE_COUNT_ANIMATION = "count_animation_events";
	public static final String RULE_COUNT_CAMERA = "count_camera_keyframes";
	public static final String RULE_COUNT_LAYERS = "count_build_layers";
	public static final String RULE_COUNT_MARKERS = "count_markers";

	private TimelineValidator() {}

	public static TimelineValidationReport validate(@Nullable Timeline document) {
		return validate(document, null, null);
	}

	public static TimelineValidationReport validate(
		@Nullable Timeline document,
		@Nullable BlockAnimationEngine engine
	) {
		return validate(document, engine, null);
	}

	public static TimelineValidationReport validate(
		@Nullable Timeline document,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		List<TimelineDiagnostic> issues = new ArrayList<>();
		if (document == null) {
			issues.add(TimelineDiagnostic.error("null_timeline", "Timeline document is null", null, Double.NaN));
			return new TimelineValidationReport(issues, 0, 0, 0, 0);
		}

		List<TimelineAnimationEvent> stageEvents = document.getStageEvents();
		int animCount = stageEvents != null ? stageEvents.size() : 0;
		int cameraCount = countCameraKeyframes(document);
		int layerCount = layerManager != null ? layerManager.getAll().size() : 0;
		int markerCount = document.getMarkers() != null ? document.getMarkers().size() : 0;

		double duration = document.getDurationSeconds();
		if (!isFinite(duration)) {
			issues.add(TimelineDiagnostic.error(
				"non_finite_timeline_duration",
				"Timeline duration is not finite: " + duration,
				null,
				Double.NaN
			));
		}

		// Validate BPM
		Object bpmVal = document.getMetadata("bpm");
		if (bpmVal instanceof Number) {
			double val = ((Number) bpmVal).doubleValue();
			if (!isFinite(val) || val <= 0) {
				issues.add(TimelineDiagnostic.error(
					"invalid_bpm",
					"Timeline has invalid BPM value: " + val,
					null,
					Double.NaN
				));
			}
		}

		// Validate reference beat times
		try {
			double[] beats = com.beatblock.timeline.ReferenceBeatResolver.resolveBeatTimesSeconds(document);
			if (beats != null) {
				for (double beat : beats) {
					if (!isFinite(beat)) {
						issues.add(TimelineDiagnostic.error(
							"non_finite_beat_time",
							"Reference beat time is not finite: " + beat,
							null,
							Double.NaN
						));
						break;
					}
				}
			}
		} catch (Exception ignored) {}

		// Validate all tracks, clips, and clip events
		for (Track track : document.getTracks()) {
			if (track == null) continue;
			for (var clip : track.getClips()) {
				if (clip == null) continue;
				double start = clip.getStartTimeSeconds();
				double end = clip.getEndTimeSeconds();
				if (!isFinite(start) || !isFinite(end) || start > end) {
					issues.add(TimelineDiagnostic.error(
						"invalid_clip_range",
						"Track \"" + track.getName() + "\" has invalid clip range: [" + start + ", " + end + "]",
						null,
						Double.NaN
					));
				}
				for (var event : clip.getEvents()) {
					if (event == null) continue;
					double eventTime = event.getTimeSeconds();
					if (!isFinite(eventTime)) {
						String rule = "non_finite_event_time";
						if (Timeline.TRACK_ID_CAMERA.equals(track.getId())) {
							rule = "non_finite_camera_time";
						} else if (Timeline.TRACK_ID_GLOBAL.equals(track.getId())) {
							rule = "non_finite_global_time";
						}
						issues.add(TimelineDiagnostic.error(
							rule,
							"Event in track \"" + track.getName() + "\" has non-finite time: " + eventTime,
							event.getId(),
							eventTime
						));
					}
				}
			}
		}

		// Validate markers
		if (document.getMarkers() != null) {
			for (var marker : document.getMarkers()) {
				if (marker == null) continue;
				if (!isFinite(marker.getTimeSeconds())) {
					issues.add(TimelineDiagnostic.error(
						"non_finite_marker_time",
						"Marker \"" + marker.getName() + "\" has non-finite time: " + marker.getTimeSeconds(),
						marker.getId(),
						marker.getTimeSeconds()
					));
				}
			}
		}

		// Validate global events list
		if (document.getGlobalEvents() != null) {
			for (var ge : document.getGlobalEvents()) {
				if (ge == null) continue;
				if (!isFinite(ge.getTimeSeconds())) {
					issues.add(TimelineDiagnostic.error(
						"non_finite_global_time",
						"Global event \"" + ge.getName() + "\" has non-finite time: " + ge.getTimeSeconds(),
						null,
						ge.getTimeSeconds()
					));
				}
			}
		}

		Set<String> seenIds = new HashSet<>();

		if (stageEvents != null) {
			for (TimelineAnimationEvent event : stageEvents) {
				if (event == null) {
					continue;
				}
				validateEvent(event, duration, engine, layerManager, seenIds, issues);
			}
		}

		// Audio path: warn when there is content to play but no bound audio / file missing
		Object audioPath = document.getMetadata("audioPath");
		String path = audioPath != null ? String.valueOf(audioPath).trim() : "";
		if (animCount > 0 && path.isBlank()) {
			issues.add(TimelineDiagnostic.warning(
				RULE_MISSING_AUDIO,
				"No audio asset path bound to the timeline",
				null,
				Double.NaN
			));
		} else if (!path.isBlank()) {
			boolean exists;
			try {
				exists = Files.isRegularFile(Path.of(path));
			} catch (RuntimeException ignored) {
				exists = false;
			}
			if (!exists) {
				issues.add(TimelineDiagnostic.warning(
					RULE_AUDIO_FILE_MISSING,
					"Audio path does not exist on disk: " + path,
					null,
					Double.NaN
				));
			}
		}

		// Summary info lines (for Performance check UI counts — always present)
		issues.add(TimelineDiagnostic.info(
			RULE_COUNT_ANIMATION,
			animCount + " animation event(s)"
		));
		issues.add(TimelineDiagnostic.info(
			RULE_COUNT_CAMERA,
			cameraCount + " camera keyframe(s)"
		));
		issues.add(TimelineDiagnostic.info(
			RULE_COUNT_LAYERS,
			layerCount + " build layer(s)"
		));
		if (markerCount > 0) {
			issues.add(TimelineDiagnostic.info(
				RULE_COUNT_MARKERS,
				markerCount + " marker(s)"
			));
		}

		return new TimelineValidationReport(issues, animCount, cameraCount, layerCount, markerCount);
	}

	private static void validateEvent(
		TimelineAnimationEvent event,
		double timelineDuration,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager,
		Set<String> seenIds,
		List<TimelineDiagnostic> issues
	) {
		String eventId = event.getEventId();
		double time = event.getTimeSeconds();
		String idLabel = eventId != null && !eventId.isBlank() ? eventId : "(no-id)";

		// Duplicate ID
		if (eventId != null && !eventId.isBlank()) {
			if (!seenIds.add(eventId)) {
				issues.add(TimelineDiagnostic.error(
					RULE_DUPLICATE_EVENT_ID,
					"Duplicate event id: " + eventId,
					eventId,
					time
				));
			}
		}

		// Duration
		double dur = event.getDurationSeconds();
		if (!isFinite(dur)) {
			issues.add(TimelineDiagnostic.error(
				RULE_NON_FINITE_EVENT_DURATION,
				"Non-finite duration for event " + idLabel + ": " + dur,
				eventId,
				time
			));
		} else if (dur <= 0) {
			issues.add(TimelineDiagnostic.error(
				RULE_NON_POSITIVE_EVENT_DURATION,
				"Non-positive duration for event " + idLabel + ": " + dur,
				eventId,
				time
			));
		}

		// Payload / unsupported params + BUILD layer binding
		try {
			StageEventPayload payload = event.getPayload();
			if (payload == null) {
				issues.add(TimelineDiagnostic.error(
					RULE_UNSUPPORTED_PAYLOAD,
					"Null payload for event " + idLabel,
					eventId,
					time
				));
			} else if (payload.actionMode() == TimelineAnimationActionMode.BUILD
				&& payload instanceof StageEventPayload.Build build) {
				String layerId = build.layerId();
				if (layerId != null && !layerId.isBlank() && layerManager != null
					&& layerManager.get(layerId) == null) {
					issues.add(TimelineDiagnostic.warning(
						RULE_MISSING_BUILD_LAYER,
						"BUILD event " + idLabel + " references missing layer \"" + layerId + "\"",
						eventId,
						time
					));
				}
			}
		} catch (RuntimeException ex) {
			issues.add(TimelineDiagnostic.error(
				RULE_UNSUPPORTED_PAYLOAD,
				"Unsupported or corrupt payload for event " + idLabel + ": " + ex.getMessage(),
				eventId,
				time
			));
		}

		// Outside timeline
		if (time < -1e-9) {
			issues.add(TimelineDiagnostic.warning(
				RULE_EVENT_OUTSIDE_TIMELINE,
				"Event " + idLabel + " starts before 0 (" + formatTime(time) + "s)",
				eventId,
				time
			));
		} else if (timelineDuration > 0 && time > timelineDuration + 1e-6) {
			issues.add(TimelineDiagnostic.warning(
				RULE_EVENT_OUTSIDE_TIMELINE,
				"Event " + idLabel + " starts after timeline end ("
					+ formatTime(time) + "s > " + formatTime(timelineDuration) + "s)",
				eventId,
				time
			));
		}

		// Target
		String targetId = event.getTargetObjectId();
		if (targetId == null || targetId.isBlank()) {
			issues.add(TimelineDiagnostic.warning(
				RULE_UNBOUND_TARGET,
				"Event " + idLabel + " has no StageObject target (unbound)",
				eventId,
				time
			));
		} else if (engine != null) {
			StageObject stage = engine.getStageObjectSystem().get(targetId);
			if (stage == null) {
				issues.add(TimelineDiagnostic.warning(
					RULE_MISSING_STAGE_OBJECT,
					"Event " + idLabel + " targets missing StageObject \"" + targetId + "\"",
					eventId,
					time
				));
			}
		}

		// Animation preset
		String animType = event.getAnimationTypeId();
		if (animType == null || animType.isBlank()) {
			issues.add(TimelineDiagnostic.error(
				RULE_MISSING_ANIMATION_PRESET,
				"Event " + idLabel + " has empty animation preset id",
				eventId,
				time
			));
		} else if (engine != null) {
			AnimationDefinition def = engine.getAnimationLibrary().get(animType);
			if (def == null) {
				// Case-insensitive fallback (library is case-sensitive by id)
				boolean found = false;
				for (String key : engine.getAnimationLibrary().getAll().keySet()) {
					if (key != null && key.equalsIgnoreCase(animType)) {
						found = true;
						break;
					}
				}
				if (!found) {
					issues.add(TimelineDiagnostic.error(
						RULE_MISSING_ANIMATION_PRESET,
						"Event " + idLabel + " references unknown animation preset \"" + animType + "\"",
						eventId,
						time
					));
				}
			}
		}
	}

	private static int countCameraKeyframes(Timeline document) {
		Track camera = document.getTrack(Timeline.TRACK_ID_CAMERA);
		if (camera == null) {
			return 0;
		}
		int n = 0;
		for (var clip : camera.getClips()) {
			if (clip == null) continue;
			for (var event : clip.getEvents()) {
				if (event != null && event.getType() == EventType.CAMERA_KEYFRAME) {
					n++;
				}
			}
		}

		return n;
	}

	private static boolean isFinite(double value) {
		return Double.isFinite(value);
	}

	private static String formatTime(double seconds) {
		return String.format(Locale.ROOT, "%.3f", seconds);
	}
}
